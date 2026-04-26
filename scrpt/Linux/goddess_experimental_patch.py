#!/usr/bin/env python3
"""
goddess_experimental_patch.py

Drop-in patch helpers for GoddessAPI.py.

Purpose:
- add datas/experimental.txt feature governance
- add datas/experimental_journal.txt change logging
- allow the AI to read log files for self-optimization reference
- prevent destructive log rewriting by exposing append-only helpers
- keep user authority distinct from AI_SELF and AI_SUGGEST modes
- govern newly added runtime features independently so users can
  enable, disable, or tune them without touching source code

Intended integration points inside GoddessAPI.py:
1. import the helpers you want from this file
2. call init_experimental_files(DATAS_DIR) during startup
3. wrap experimental behaviors with is_feature_enabled(...)
4. allow AI self-toggles only through set_feature_flag(..., actor="AI_SELF", ...)
5. use read_log_memory(...) for analysis and append_log_memory(...) for append-only writes

Feature flag format:   FEATURE=MODE:STATE
  MODE:  USER        -> only user may change
         AI_SUGGEST  -> AI may propose, user must confirm
         AI_SELF     -> AI may change directly (low-risk, reversible)
  STATE: on | off

Extended flags also support numeric tuning values:
  FEATURE=MODE:STATE:VALUE   (VALUE is feature-specific; STATE is still on|off)
"""

from __future__ import annotations

import datetime as _dt
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# ── DEFAULT FEATURE GOVERNANCE ───────────────────────────────────────────────
# Defaults are conservative for side-effectful or external features,
# permissive for local, reversible, non-destructive ones.

DEFAULT_EXPERIMENTAL_FLAGS: Dict[str, str] = {

    # ── ORIGINAL CHATGPT FLAGS ────────────────────────────────────────────────
    # These were present in the original patch; kept and preserved exactly.
    "ARCHIVIST_PASS":             "USER:on",
    "HEURISTIC_EXTRACTION":       "USER:on",
    "AUTO_COMPRESS_THEORIES":     "USER:on",
    "REMOTE_LLM_ROUTING":         "USER:off",
    "TOOL_MANIFEST_EXECUTION":    "USER:off",
    "VOCAB_SELF_MUTATION":        "AI_SELF:on",
    "RESPONSE_STYLE_EVOLUTION":   "AI_SELF:on",
    "AUTO_EXPERIMENTAL_TOGGLES":  "AI_SUGGEST:on",
    "LOG_SELF_OPTIMIZATION_READ": "AI_SELF:on",
    "LOG_APPEND_MEMORY":          "AI_SELF:on",

    # ── AVATAR / PROTOCOL TAGS ────────────────────────────────────────────────
    # EMIT_ACTION_TAGS: whether [ACTION] protocol tags are sent to Java.
    #   Disable if Mode B is not in use and the tag output is unwanted.
    "EMIT_ACTION_TAGS":           "USER:on",

    # ── IMAGE GGUF ────────────────────────────────────────────────────────────
    # IMAGE_GGUF_INTAKE: whether image files dropped in intake/ are processed
    #   by the image GGUF. Requires a (image)-named .gguf file to be present.
    #   Disable to keep image files unprocessed (they will not be moved).
    "IMAGE_GGUF_INTAKE":          "USER:on",

    # ── TOOL SYSTEM ───────────────────────────────────────────────────────────
    # TOOL_AUTO_FIRE: whether enabled tools fire automatically when a query
    #   contains a matching TAGS keyword, without needing an explicit invocation.
    #   If off, tools only run when the user says "use <tool>" or "run <tool>".
    "TOOL_AUTO_FIRE":             "USER:on",

    # TOOL_INTENT_GGUF: whether the GGUF model is used to classify tool intent.
    #   If off, keyword matching is always used regardless of GGUF availability.
    #   Disable if GGUF intent classification is producing false positives.
    "TOOL_INTENT_GGUF":           "USER:on",

    # ── KNOWLEDGE PIPELINE ────────────────────────────────────────────────────
    # ENCYCLOPEDIA_PROMOTION: whether religion.txt theories are promoted to
    #   encyclopedia.txt at all. Disable to freeze the validated overlay at
    #   its current state and let theories accumulate without being applied.
    "ENCYCLOPEDIA_PROMOTION":     "USER:on",

    # PERSONA_SELF_MODIFICATION: whether the AI may write to persona.txt.
    #   If off, persona.txt is read-only during the session (can still be
    #   edited manually by the user). Disable if you want full control over
    #   what the AI records about itself.
    "PERSONA_SELF_MODIFICATION":  "AI_SELF:on",

    # OUTPUT_VOCAB_EVOLUTION: whether the AI may add new entries to the output
    #   vocabulary file during prompts, resource scans, and session events.
    #   Disable to lock the vocabulary at its current state.
    "OUTPUT_VOCAB_EVOLUTION":     "AI_SELF:on",

    # ── DE-ESCALATION ─────────────────────────────────────────────────────────
    # DE_ESCALATION_RESPONSE: whether the AI monitors its own response patterns
    #   and applies de-escalation when it detects repeated high-intensity or
    #   looping output. When enabled, the AI will:
    #     - reduce response length if output has grown longer over 3+ consecutive
    #       responses on the same topic
    #     - insert a pause/summary beat before re-engaging dense reasoning chains
    #     - prefer direct answers over elaboration when the journal shows the
    #       same query has been answered multiple times in the same session
    #   This does not suppress content — it adjusts delivery pacing only.
    #   Authority: AI_SELF because it is local, reversible, and non-destructive.
    "DE_ESCALATION_RESPONSE":     "AI_SELF:on",

    # DE_ESCALATION_SENSITIVITY: numeric tuning for how quickly de-escalation
    #   triggers. Format: AI_SELF:on:N where N is 1 (hair-trigger) to 5 (relaxed).
    #   Default 3 — triggers after 3 consecutive escalating patterns.
    "DE_ESCALATION_SENSITIVITY":  "USER:on:3",

    # ── MEMORY DEGRADATION ────────────────────────────────────────────────────
    # MEMORY_DEGRADATION: whether knowledge file entries age over time.
    #   When enabled, the optimizer tracks when entries were last referenced
    #   and, after MEMORY_DEGRADATION_DAYS days without a hit, marks them
    #   STALE in their source file. Stale entries are still readable but are
    #   deprioritised in search results. They are never deleted automatically.
    #   The AI may propose removal via AI_SUGGEST; the user must confirm.
    #
    #   This prevents the knowledge base from growing indefinitely with entries
    #   that are never referenced again. Long-lived sessions benefit most.
    "MEMORY_DEGRADATION":         "USER:off",

    # MEMORY_DEGRADATION_DAYS: how many days without a reference before an
    #   entry is marked STALE. Format: USER:off:N. Default 30.
    #   Only active when MEMORY_DEGRADATION is on.
    "MEMORY_DEGRADATION_DAYS":    "USER:off:30",

    # MEMORY_DEGRADATION_COMPRESS: whether stale entries are automatically
    #   compressed into the almanac (one-line summary) by the GGUF before removal.
    #   Requires MEMORY_DEGRADATION to be on and GGUF to be available.
    #   Authority: USER because compression is permanent (entries removed).
    "MEMORY_DEGRADATION_COMPRESS": "USER:off",

    # CONTEXT_DECAY: whether the hibernate context window is trimmed over time.
    #   When enabled, context entries older than CONTEXT_DECAY_DAYS are removed
    #   from the hiberfile at shutdown, keeping the context window fresh.
    #   The journal is not affected — only the in-session context list.
    "CONTEXT_DECAY":              "AI_SELF:on",

    # CONTEXT_DECAY_DAYS: entries older than this are removed from context
    #   at shutdown when CONTEXT_DECAY is on. Default 7 days.
    "CONTEXT_DECAY_DAYS":         "AI_SELF:on:7",
}

_COMMENT_HEADER = """# experimental.txt
# FEATURE GOVERNANCE FILE
# Format: FEATURE=MODE:STATE  or  FEATURE=MODE:STATE:VALUE
# MODE  = USER | AI_SUGGEST | AI_SELF
# STATE = on | off
# VALUE = optional numeric tuning parameter (feature-specific)
#
# USER       -> only the user should change the value
# AI_SUGGEST -> the AI may propose a change, but not apply it directly
# AI_SELF    -> the AI may apply the change itself if it is reversible and safe
#
# Features and their intent:
#
# ORIGINAL FLAGS:
#   ARCHIVIST_PASS             - GGUF audits/refactors encyclopedia entries (every 5th optimizer cycle)
#   HEURISTIC_EXTRACTION       - heuristic text analysis during intake
#   AUTO_COMPRESS_THEORIES     - Mycelial Compression: compress old low-scoring theories to almanac
#   REMOTE_LLM_ROUTING         - allow routing queries to online LLM providers (future feature)
#   TOOL_MANIFEST_EXECUTION    - allow AI to execute /resources binaries
#   VOCAB_SELF_MUTATION        - AI may add new entries to output_vocabulary.txt
#   RESPONSE_STYLE_EVOLUTION   - AI may adjust response formatting over time
#   AUTO_EXPERIMENTAL_TOGGLES  - AI may propose feature flag changes
#   LOG_SELF_OPTIMIZATION_READ - AI may read log files for self-optimization reference
#   LOG_APPEND_MEMORY          - AI may append notes to approved log files
#
# AVATAR / PROTOCOL TAGS:
#   EMIT_ACTION_TAGS           - send [ACTION] tags to Java Mode B avatar
#
# IMAGE GGUF:
#   IMAGE_GGUF_INTAKE          - process image files via (image)-named GGUF model
#
# TOOL SYSTEM:
#   TOOL_AUTO_FIRE             - enabled tools fire automatically on keyword match
#   TOOL_INTENT_GGUF           - use GGUF model for tool intent classification
#
# KNOWLEDGE PIPELINE:
#   ENCYCLOPEDIA_PROMOTION     - allow theories to promote from religion.txt to encyclopedia.txt
#   PERSONA_SELF_MODIFICATION  - AI may write to persona.txt
#   OUTPUT_VOCAB_EVOLUTION     - AI may evolve output_vocabulary.txt at runtime events
#
# DE-ESCALATION:
#   DE_ESCALATION_RESPONSE     - AI moderates delivery pacing when output is escalating
#   DE_ESCALATION_SENSITIVITY  - trigger threshold 1 (hair-trigger) to 5 (relaxed); default 3
#
# MEMORY DEGRADATION:
#   MEMORY_DEGRADATION         - mark knowledge entries STALE after N days without reference
#   MEMORY_DEGRADATION_DAYS    - days without reference before STALE marking; default 30
#   MEMORY_DEGRADATION_COMPRESS- GGUF-compress stale entries before removal (requires GGUF)
#   CONTEXT_DECAY              - trim old context entries from hiberfile at shutdown
#   CONTEXT_DECAY_DAYS         - context entries older than N days are removed; default 7
"""

_JOURNAL_HEADER = """# experimental_journal.txt
# Append-only record of feature proposals and changes.
"""


# ── PATH HELPERS ─────────────────────────────────────────────────────────────

def experimental_path(datas_dir: Path) -> Path:
    return datas_dir / "experimental.txt"

def experimental_journal_path(datas_dir: Path) -> Path:
    return datas_dir / "experimental_journal.txt"

def log_memory_index_path(datas_dir: Path) -> Path:
    return datas_dir / "log_memory_index.txt"


# ── INITIALIZATION ───────────────────────────────────────────────────────────

def init_experimental_files(datas_dir: Path) -> None:
    datas_dir.mkdir(parents=True, exist_ok=True)

    exp = experimental_path(datas_dir)
    if not exp.exists():
        body = "\n".join(f"{k}={v}" for k, v in DEFAULT_EXPERIMENTAL_FLAGS.items())
        exp.write_text(_COMMENT_HEADER + "\n" + body + "\n", encoding="utf-8")
    else:
        existing_text = exp.read_text(encoding="utf-8", errors="ignore")
        existing = {}
        for line in existing_text.splitlines():
            parsed = _parse_feature_line(line)
            if parsed:
                k, v = parsed
                existing[k] = v

        missing = {
            k: v for k, v in DEFAULT_EXPERIMENTAL_FLAGS.items()
            if k not in existing
        }

        if missing:
            with exp.open("a", encoding="utf-8") as f:
                f.write("\n# Added automatically by updated experimental defaults\n")
                for k, v in missing.items():
                    f.write(f"{k}={v}\n")

    journal = experimental_journal_path(datas_dir)
    if not journal.exists():
        journal.write_text(_JOURNAL_HEADER, encoding="utf-8")

    idx = log_memory_index_path(datas_dir)
    if not idx.exists():
        idx.write_text(
            "# log_memory_index.txt\n"
            "# Whitelisted append-only memory/log files for AI self-optimization review.\n",
            encoding="utf-8",
        )


# ── PARSING ──────────────────────────────────────────────────────────────────

def _parse_feature_line(line: str) -> Optional[Tuple[str, str]]:
    """Parse a feature line. Returns (name, raw_value) or None."""
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        return None
    feature, _, raw = line.partition("=")
    feature = feature.strip()
    raw = raw.strip()
    if ":" not in raw:
        return None
    mode, _, value = raw.partition(":")
    mode = mode.strip().upper()
    value = value.strip()
    if mode not in {"USER", "AI_SUGGEST", "AI_SELF"}:
        return None
    if not value:
        return None
    return feature, f"{mode}:{value}"  # return full raw including optional :VALUE


def load_experimental_flags(datas_dir: Path) -> Dict[str, str]:
    init_experimental_files(datas_dir)
    flags = dict(DEFAULT_EXPERIMENTAL_FLAGS)
    for line in experimental_path(datas_dir).read_text(encoding="utf-8", errors="ignore").splitlines():
        parsed = _parse_feature_line(line)
        if parsed:
            k, v = parsed
            flags[k] = v
    return flags


def feature_mode(flags: Dict[str, str], name: str) -> str:
    raw = flags.get(name, DEFAULT_EXPERIMENTAL_FLAGS.get(name, "USER:off"))
    return raw.split(":", 1)[0]


def feature_state(flags: Dict[str, str], name: str) -> str:
    raw = flags.get(name, DEFAULT_EXPERIMENTAL_FLAGS.get(name, "USER:off"))
    parts = raw.split(":")
    return parts[1] if len(parts) > 1 else "off"


def feature_value(flags: Dict[str, str], name: str, default: int = 0) -> int:
    """Return the optional numeric VALUE from a MODE:STATE:VALUE flag."""
    raw = flags.get(name, DEFAULT_EXPERIMENTAL_FLAGS.get(name, f"USER:off:{default}"))
    parts = raw.split(":")
    if len(parts) > 2:
        try:
            return int(parts[2])
        except ValueError:
            pass
    return default

def feature_value_str(flags: Dict[str, str], name: str, default: str = "") -> str:
    raw = flags.get(name, DEFAULT_EXPERIMENTAL_FLAGS.get(name, f"USER:{default}"))
    parts = raw.split(":")
    return parts[1] if len(parts) > 1 else default

def get_config_value(flags: Dict[str, str], name: str, default: str = "auto") -> str:
    return feature_value_str(flags, name, default) 

def resolve_config_path(
    flags: Dict[str, str],
    name: str,
    *,
    base_dir: Path,
    auto_pattern: str | None = None,
) -> Path | None:
    value = get_config_value(flags, name, "auto").strip()

    if value.lower() in {"", "none", "off", "disabled"}:
        return None

    if value.lower() == "auto":
        if not auto_pattern:
            return None
        matches = sorted(base_dir.glob(auto_pattern))
        return matches[0] if matches else None

    p = Path(value).expanduser()
    if not p.is_absolute():
        p = base_dir / p

    return p if p.exists() else None


def is_feature_enabled(flags: Dict[str, str], name: str) -> bool:
    return feature_state(flags, name) == "on"


def can_ai_toggle(flags: Dict[str, str], name: str) -> bool:
    return feature_mode(flags, name) == "AI_SELF"


def can_ai_suggest(flags: Dict[str, str], name: str) -> bool:
    return feature_mode(flags, name) in {"AI_SELF", "AI_SUGGEST"}


# ── APPEND-ONLY CHANGE JOURNAL ────────────────────────────────────────────────

def _ts() -> str:
    return _dt.datetime.now().isoformat()


def append_experimental_journal(
    datas_dir: Path,
    *,
    actor: str,
    feature: str,
    old_value: str,
    new_value: str,
    reason: str,
    applied: bool,
) -> None:
    init_experimental_files(datas_dir)
    journal = experimental_journal_path(datas_dir)
    with journal.open("a", encoding="utf-8") as f:
        f.write(
            f"\n[{_ts()}]\n"
            f"ACTOR={actor}\n"
            f"FEATURE={feature}\n"
            f"FROM={old_value}\n"
            f"TO={new_value}\n"
            f"APPLIED={'yes' if applied else 'no'}\n"
            f"REASON={reason}\n"
        )


# ── FLAG MUTATION / PROPOSAL ──────────────────────────────────────────────────

def _rewrite_feature_file(datas_dir: Path, flags: Dict[str, str]) -> None:
    exp = experimental_path(datas_dir)
    lines = [_COMMENT_HEADER.rstrip(), ""]
    lines.extend(f"{k}={v}" for k, v in sorted(flags.items()))
    exp.write_text("\n".join(lines) + "\n", encoding="utf-8")


def set_feature_flag(
    datas_dir: Path,
    *,
    feature: str,
    state: str,
    actor: str,
    reason: str,
) -> bool:
    actor = actor.upper().strip()
    state = state.strip()

    if not state:
        raise ValueError("state must not be empty")

    flags = load_experimental_flags(datas_dir)
    old_value = flags.get(feature, DEFAULT_EXPERIMENTAL_FLAGS.get(feature, "USER:off"))
    mode = feature_mode(flags, feature)

    allowed = (
        actor == "USER"
        or (actor == "AI_SELF" and mode == "AI_SELF")
    )

    new_value = f"{mode}:{state}"

    if not allowed:
        append_experimental_journal(
            datas_dir,
            actor=actor,
            feature=feature,
            old_value=old_value,
            new_value=new_value,
            reason=reason,
            applied=False,
        )
        return False

    flags[feature] = new_value
    _rewrite_feature_file(datas_dir, flags)

    append_experimental_journal(
        datas_dir,
        actor=actor,
        feature=feature,
        old_value=old_value,
        new_value=new_value,
        reason=reason,
        applied=True,
    )

    return True


def set_feature_value(
    datas_dir: Path,
    *,
    feature: str,
    value: int,
    actor: str,
    reason: str,
) -> bool:
    """Update only the numeric VALUE component of a flag, leaving STATE unchanged."""
    flags    = load_experimental_flags(datas_dir)
    cur_state = feature_state(flags, feature)
    return set_feature_flag(
        datas_dir, feature=feature, state=cur_state,
        actor=actor, reason=reason, value=value
    )


def propose_feature_toggle(
    datas_dir: Path,
    *,
    feature: str,
    state: str,
    actor: str = "AI_SUGGEST",
    reason: str,
) -> None:
    flags     = load_experimental_flags(datas_dir)
    mode      = feature_mode(flags, feature)
    old_value = flags.get(feature, DEFAULT_EXPERIMENTAL_FLAGS.get(feature, "USER:off"))
    new_value = f"{mode}:{state.lower().strip()}"
    append_experimental_journal(
        datas_dir, actor=actor, feature=feature,
        old_value=old_value, new_value=new_value,
        reason=reason, applied=False,
    )


# ── MEMORY DEGRADATION HELPERS ────────────────────────────────────────────────
# These helpers are called by the optimizer loop when MEMORY_DEGRADATION is on.
# They work on the flat-text knowledge files (dictionary, thesaurus, almanac).
# Entries are never deleted — they are marked STALE and deprioritised.

def mark_stale_entries(filepath: Path, stale_after_days: int, journal_path: Path) -> int:
    """
    Walk a knowledge file and mark entries that have no LAST_SEEN timestamp
    newer than stale_after_days. Adds a STALE=yes line after the entry header.
    Returns the number of entries newly marked stale.
    """
    if not filepath.exists():
        return 0
    cutoff = _dt.datetime.now() - _dt.timedelta(days=stale_after_days)
    content = filepath.read_text(encoding="utf-8", errors="ignore")
    lines   = content.splitlines(keepends=True)
    changed = 0
    out     = []
    i = 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        # Detect an entry header (headword line)
        if re.match(r'^\S+ \(n\.\)', line.strip()):
            # Look ahead for LAST_SEEN or STALE within the next 8 lines
            block_end = min(i + 8, len(lines))
            block     = "".join(lines[i:block_end])
            if "STALE=yes" in block:
                i += 1; continue   # already stale
            m = re.search(r'LAST_SEEN=(\S+)', block)
            if m:
                try:
                    seen = _dt.datetime.fromisoformat(m.group(1))
                    if seen >= cutoff:
                        i += 1; continue   # referenced recently, skip
                except ValueError:
                    pass
            # No recent LAST_SEEN — mark stale
            out.append(f"  STALE=yes  MARKED={_dt.datetime.now().date()}\n")
            changed += 1
        i += 1
    if changed:
        filepath.write_text("".join(out), encoding="utf-8")
        with journal_path.open("a", encoding="utf-8") as f:
            f.write(
                f"\n[{_ts()}] MEMORY_DEGRADATION: marked {changed} entries "
                f"STALE in {filepath.name} (threshold={stale_after_days}d)\n"
            )
    return changed


def update_entry_last_seen(filepath: Path, headword: str) -> None:
    """
    Update or insert LAST_SEEN timestamp for a headword in a knowledge file.
    Called by the reasoning engine when it returns a result for a word.
    Only updates if the entry exists and is not already marked within today.
    """
    if not filepath.exists():
        return
    today = _dt.datetime.now().isoformat()
    content = filepath.read_text(encoding="utf-8", errors="ignore")
    pattern = rf'(^{re.escape(headword)} \(n\.\).*?)(LAST_SEEN=\S+)?'
    if not re.search(rf'^{re.escape(headword)} \(n\.\)', content, re.M):
        return
    # Update existing LAST_SEEN or append one after the headword line
    if re.search(rf'^{re.escape(headword)} \(n\.\).*\nLAST_SEEN=', content, re.M):
        content = re.sub(
            rf'(^{re.escape(headword)} \(n\.\).*?\n)LAST_SEEN=\S+',
            rf'\g<1>LAST_SEEN={today}',
            content, flags=re.M
        )
    else:
        content = re.sub(
            rf'(^{re.escape(headword)} \(n\.\))',
            rf'\g<1>\n  LAST_SEEN={today}',
            content, flags=re.M, count=1
        )
    filepath.write_text(content, encoding="utf-8")


# ── CONTEXT DECAY HELPER ──────────────────────────────────────────────────────

def decay_context(context: list, decay_days: int) -> Tuple[list, int]:
    """
    Remove context entries older than decay_days from a context list.
    Each entry is expected to have a 'ts' dict with a 'unix' key.
    Returns (trimmed_context, removed_count).
    """
    if not context or decay_days <= 0:
        return context, 0
    cutoff  = _dt.datetime.now().timestamp() - (decay_days * 86400)
    trimmed = [e for e in context if e.get("ts", {}).get("unix", 0) >= cutoff]
    removed = len(context) - len(trimmed)
    return trimmed, removed


# ── DE-ESCALATION HELPERS ─────────────────────────────────────────────────────

def check_de_escalation(
    flags: Dict[str, str],
    response_history: List[int],
    sensitivity: int = 3,
) -> bool:
    """
    Returns True if the AI should apply de-escalation pacing to its next response.
    response_history is a list of recent response lengths (in chars).
    Triggers when the last `sensitivity` responses show a monotonically increasing
    length trend, suggesting runaway elaboration.
    """
    if not is_feature_enabled(flags, "DE_ESCALATION_RESPONSE"):
        return False
    if len(response_history) < sensitivity:
        return False
    recent = response_history[-sensitivity:]
    # Monotonically increasing = escalating
    return all(recent[i] < recent[i+1] for i in range(len(recent)-1))


def de_escalation_note() -> str:
    """Returns a brief de-escalation framing note the AI can prepend internally."""
    return (
        "[DE-ESCALATION: response pacing applied — "
        "summarising rather than elaborating this cycle]"
    )


# ── LOG MEMORY ACCESS ─────────────────────────────────────────────────────────

DEFAULT_LOGFILE_KEYS = {
    "journal":              "journal.txt",
    "intake":               "intake.log",
    "session_profile":      "session_profile.txt",
    "experimental_journal": "experimental_journal.txt",
    "log_memory_index":     "log_memory_index.txt",
}


def get_readable_logfiles(datas_dir: Path, convodata_dir: Path) -> Dict[str, Path]:
    files = {
        "journal":              datas_dir / "journal.txt",
        "intake":               datas_dir.parent / "intake" / "intake.log",
        "session_profile":      datas_dir.parent / "system" / "session_profile.txt",
        "experimental_journal": datas_dir / "experimental_journal.txt",
        "session_runtime":      convodata_dir / "convodata.txt",
        "log_memory_index":     datas_dir / "log_memory_index.txt",
    }
    return {k: v for k, v in files.items() if v.exists()}


def read_log_memory(
    datas_dir: Path,
    convodata_dir: Path,
    *,
    name: Optional[str] = None,
    tail_lines: int = 200,
) -> str:
    flags = load_experimental_flags(datas_dir)
    if not is_feature_enabled(flags, "LOG_SELF_OPTIMIZATION_READ"):
        return ""
    available = get_readable_logfiles(datas_dir, convodata_dir)

    def _tail(path: Path) -> str:
        lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
        return "\n".join(lines[-tail_lines:])

    if name:
        path = available.get(name)
        return _tail(path) if path else ""

    parts: List[str] = []
    for key, path in available.items():
        parts.append(f"\n### LOG:{key} ###\n{_tail(path)}")
    return "\n".join(parts).strip()


def append_log_memory(
    datas_dir: Path,
    convodata_dir: Path,
    *,
    target: str,
    note: str,
    actor: str = "AI_SELF",
) -> bool:
    flags = load_experimental_flags(datas_dir)
    if not is_feature_enabled(flags, "LOG_APPEND_MEMORY"):
        return False
    available = get_readable_logfiles(datas_dir, convodata_dir)
    path = available.get(target)
    if not path:
        return False
    stamp = _ts()
    with path.open("a", encoding="utf-8") as f:
        f.write(
            f"\n[{stamp}] APPEND_ONLY_MEMORY\n"
            f"ACTOR={actor}\n"
            f"NOTE={note}\n"
        )
    return True


# ── SUGGESTION ENGINE ─────────────────────────────────────────────────────────

def suggest_experimental_adjustments_from_logs(
    datas_dir: Path,
    convodata_dir: Path,
) -> List[Tuple[str, str]]:
    """
    Inspects log text and returns (feature, suggestion) tuples.
    Does not apply changes — suggestions only.
    """
    text = read_log_memory(datas_dir, convodata_dir, name=None, tail_lines=300)
    out:  List[Tuple[str, str]] = []
    if not text:
        return out

    if len(re.findall(r"GGUF QUERY ERROR|GGUF load failed|Consultation Protocol failed", text, re.I)) >= 2:
        out.append(("ARCHIVIST_PASS",
                    "Consider disabling if repeated GGUF failures continue."))

    if len(re.findall(r"duplicate|out-of-order|deduplicate", text, re.I)) >= 3:
        out.append(("AUTO_COMPRESS_THEORIES",
                    "Knowledge churn suggests compression/review may be useful."))

    if len(re.findall(r"heuristic deduced|Added to maths", text, re.I)) >= 3:
        out.append(("HEURISTIC_EXTRACTION",
                    "Heuristic extraction appears active and useful."))

    # Memory degradation suggestion: if journal shows no stale marking but
    # dict/thesaurus entries are being written and never referenced
    if len(re.findall(r"dict:\+\d+", text, re.I)) >= 5 and \
       len(re.findall(r"LAST_SEEN|STALE", text, re.I)) == 0:
        out.append(("MEMORY_DEGRADATION",
                    "Many entries written with no reference tracking. "
                    "Consider enabling MEMORY_DEGRADATION to prevent stale accumulation."))

    # De-escalation suggestion: if responses have been growing
    lengths = re.findall(r"RESPONSE LEN: (\d+) chars", text)
    if len(lengths) >= 5:
        lens = [int(x) for x in lengths[-5:]]
        if all(lens[i] < lens[i+1] for i in range(4)):
            out.append(("DE_ESCALATION_RESPONSE",
                        "Response lengths are consistently growing. "
                        "DE_ESCALATION_RESPONSE is already on — consider lowering sensitivity."))

    return out


# ── INTEGRATION NOTES ─────────────────────────────────────────────────────────
#
# Suggested minimal integration inside GoddessAPI.py:
#
#   from goddess_experimental_patch import (
#       init_experimental_files, load_experimental_flags, is_feature_enabled,
#       can_ai_toggle, set_feature_flag, set_feature_value, propose_feature_toggle,
#       feature_value, read_log_memory, append_log_memory,
#       mark_stale_entries, update_entry_last_seen, decay_context,
#       check_de_escalation, de_escalation_note,
#   )
#
#   # startup:
#   init_experimental_files(DATAS_DIR)
#   EXP_FLAGS = load_experimental_flags(DATAS_DIR)
#
#   # gate features:
#   if is_feature_enabled(EXP_FLAGS, "EMIT_ACTION_TAGS"):
#       emit_action("IDLE")
#
#   if is_feature_enabled(EXP_FLAGS, "MEMORY_DEGRADATION"):
#       days = feature_value(EXP_FLAGS, "MEMORY_DEGRADATION_DAYS", default=30)
#       mark_stale_entries(DICTIONARY, days, experimental_journal_path(DATAS_DIR))
#
#   if is_feature_enabled(EXP_FLAGS, "CONTEXT_DECAY"):
#       days = feature_value(EXP_FLAGS, "CONTEXT_DECAY_DAYS", default=7)
#       self.context, removed = decay_context(self.context, days)
#
#   # de-escalation check before responding:
#   if check_de_escalation(EXP_FLAGS, self.response_lengths):
#       # reduce length, prefer summary over elaboration this cycle
#       ...
#
# ── CONTRIBUTIONS ─────────────────────────────────────────────────────────────
# ChatGPT (OpenAI):
#   Original patch design: experimental.txt governance layer, USER/AI_SUGGEST/
#   AI_SELF authority modes, append-only journal, log memory access helpers,
#   feature mutation and proposal helpers, suggestion engine.
#
# Claude (Anthropic):
#   Extended flag set: EMIT_ACTION_TAGS, IMAGE_GGUF_INTAKE, TOOL_AUTO_FIRE,
#   TOOL_INTENT_GGUF, ENCYCLOPEDIA_PROMOTION, PERSONA_SELF_MODIFICATION,
#   OUTPUT_VOCAB_EVOLUTION, DE_ESCALATION_RESPONSE, DE_ESCALATION_SENSITIVITY,
#   MEMORY_DEGRADATION, MEMORY_DEGRADATION_DAYS, MEMORY_DEGRADATION_COMPRESS,
#   CONTEXT_DECAY, CONTEXT_DECAY_DAYS.
#   Added: feature_value() for numeric VALUE component, set_feature_value()
#   helper, mark_stale_entries(), update_entry_last_seen(), decay_context(),
#   check_de_escalation(), de_escalation_note().
#   Updated: _parse_feature_line() to handle MODE:STATE:VALUE format.
#   Updated: get_readable_logfiles() to use correct dgapi/ subdirectory paths.
