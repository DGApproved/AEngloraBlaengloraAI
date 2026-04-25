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

Intended integration points inside GoddessAPI.py:
1. import the helpers you want from this file
2. call init_experimental_files(DATAS_DIR) during startup
3. wrap experimental behaviors with is_feature_enabled(...)
4. allow AI self-toggles only through set_feature_flag(..., actor="AI_SELF", ...)
5. use read_log_memory(...) for analysis and append_log_memory(...) for append-only writes
"""

from __future__ import annotations

import datetime as _dt
import re
from pathlib import Path
from typing import Dict, List, Tuple

# ── DEFAULT FEATURE GOVERNANCE ───────────────────────────────────────────────
# Format:
#   FEATURE=MODE:STATE
#
# MODE:
#   USER       -> only user may change
#   AI_SUGGEST -> AI may propose, user must apply
#   AI_SELF    -> AI may change directly if the feature is low-risk
#
# STATE:
#   on | off
#
# These defaults are intentionally conservative for external and side-effectful
# features, and permissive only for local, reversible, non-destructive ones.

DEFAULT_EXPERIMENTAL_FLAGS = {
    "ARCHIVIST_PASS": "USER:on",
    "HEURISTIC_EXTRACTION": "USER:on",
    "AUTO_COMPRESS_THEORIES": "USER:on",
    "REMOTE_LLM_ROUTING": "USER:off",
    "TOOL_MANIFEST_EXECUTION": "USER:off",
    "VOCAB_SELF_MUTATION": "AI_SELF:on",
    "RESPONSE_STYLE_EVOLUTION": "AI_SELF:on",
    "AUTO_EXPERIMENTAL_TOGGLES": "AI_SUGGEST:on",
    "LOG_SELF_OPTIMIZATION_READ": "AI_SELF:on",
    "LOG_APPEND_MEMORY": "AI_SELF:on",
}

_COMMENT_HEADER = """# experimental.txt
# FEATURE GOVERNANCE FILE
# Format: FEATURE=MODE:STATE
# MODE = USER | AI_SUGGEST | AI_SELF
# STATE = on | off
#
# USER       -> only the user should change the value
# AI_SUGGEST -> the AI may propose a change, but not apply it directly
# AI_SELF    -> the AI may apply the change itself if it is reversible and safe
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

    journal = experimental_journal_path(datas_dir)
    if not journal.exists():
        journal.write_text(_JOURNAL_HEADER, encoding="utf-8")

    idx = log_memory_index_path(datas_dir)
    if not idx.exists():
        idx.write_text(
            "# log_memory_index.txt\n"
            "# Whitelisted append-only memory/log files can be listed here for review.\n",
            encoding="utf-8",
        )


# ── PARSING ──────────────────────────────────────────────────────────────────


def _parse_feature_line(line: str) -> Tuple[str, str] | None:
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        return None
    feature, _, raw = line.partition("=")
    feature = feature.strip()
    raw = raw.strip()
    if ":" not in raw:
        return None
    mode, _, state = raw.partition(":")
    mode = mode.strip().upper()
    state = state.strip().lower()
    if mode not in {"USER", "AI_SUGGEST", "AI_SELF"}:
        return None
    if state not in {"on", "off"}:
        return None
    return feature, f"{mode}:{state}"


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
    return raw.split(":", 1)[1]


def is_feature_enabled(flags: Dict[str, str], name: str) -> bool:
    return feature_state(flags, name) == "on"


def can_ai_toggle(flags: Dict[str, str], name: str) -> bool:
    return feature_mode(flags, name) == "AI_SELF"


def can_ai_suggest(flags: Dict[str, str], name: str) -> bool:
    return feature_mode(flags, name) in {"AI_SELF", "AI_SUGGEST"}


# ── APPEND-ONLY CHANGE JOURNAL ──────────────────────────────────────────────


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


# ── FLAG MUTATION / PROPOSAL ─────────────────────────────────────────────────


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
    """
    Returns True if applied, False if blocked by governance.
    """
    actor = actor.upper().strip()
    state = state.lower().strip()
    if state not in {"on", "off"}:
        raise ValueError("state must be 'on' or 'off'")

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


def propose_feature_toggle(
    datas_dir: Path,
    *,
    feature: str,
    state: str,
    actor: str = "AI_SUGGEST",
    reason: str,
) -> None:
    flags = load_experimental_flags(datas_dir)
    mode = feature_mode(flags, feature)
    old_value = flags.get(feature, DEFAULT_EXPERIMENTAL_FLAGS.get(feature, "USER:off"))
    new_value = f"{mode}:{state.lower().strip()}"
    append_experimental_journal(
        datas_dir,
        actor=actor,
        feature=feature,
        old_value=old_value,
        new_value=new_value,
        reason=reason,
        applied=False,
    )


# ── LOG MEMORY ACCESS ────────────────────────────────────────────────────────
# The AI may read these for self-optimization reference if the feature is on.
# The AI may not rewrite them through these helpers; append-only is enforced.

DEFAULT_LOGFILE_KEYS = {
    "journal": "journal.txt",
    "intake": "intake.log",
    "session_profile": "session_profile.txt",
    "experimental_journal": "experimental_journal.txt",
    "log_memory_index": "log_memory_index.txt",
}


def get_readable_logfiles(datas_dir: Path, convodata_dir: Path) -> Dict[str, Path]:
    files = {
        "journal": datas_dir / "journal.txt",
        "intake": Path("intake") / "intake.log",
        "session_profile": datas_dir / "session_profile.txt",
        "experimental_journal": datas_dir / "experimental_journal.txt",
        "session_runtime": convodata_dir / "convodata.txt",
        "log_memory_index": datas_dir / "log_memory_index.txt",
    }
    return {k: v for k, v in files.items() if v.exists()}


def read_log_memory(
    datas_dir: Path,
    convodata_dir: Path,
    *,
    name: str | None = None,
    tail_lines: int = 200,
) -> str:
    """
    Read-only helper for self-optimization reference.
    If name is None, concatenates short tails from all readable logs.
    """
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
    """
    Append-only helper. Never rewrites historical log content.
    Allowed targets are the whitelisted log files returned by get_readable_logfiles().
    """
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


# ── OPTIONAL QUERY-AID HELPERS ───────────────────────────────────────────────


def suggest_experimental_adjustments_from_logs(
    datas_dir: Path,
    convodata_dir: Path,
) -> List[Tuple[str, str]]:
    """
    Lightweight helper that inspects log text and returns suggestions.
    It does not apply changes.
    """
    text = read_log_memory(datas_dir, convodata_dir, name=None, tail_lines=300)
    out: List[Tuple[str, str]] = []

    if not text:
        return out

    if len(re.findall(r"GGUF QUERY ERROR|GGUF load failed|Consultation Protocol failed", text, re.I)) >= 2:
        out.append(("ARCHIVIST_PASS", "Consider disabling if repeated GGUF failures continue."))

    if len(re.findall(r"duplicate|out-of-order|deduplicate", text, re.I)) >= 3:
        out.append(("AUTO_COMPRESS_THEORIES", "Knowledge churn suggests compression/review may be useful."))

    if len(re.findall(r"heuristic deduced|Added to maths", text, re.I)) >= 3:
        out.append(("HEURISTIC_EXTRACTION", "Heuristic extraction appears active and useful."))

    return out


# ── INTEGRATION NOTES (comments only) ───────────────────────────────────────
#
# Suggested minimal integration inside GoddessAPI.py:
#
#   from goddess_experimental_patch import (
#       init_experimental_files, load_experimental_flags, is_feature_enabled,
#       can_ai_toggle, set_feature_flag, propose_feature_toggle,
#       read_log_memory, append_log_memory
#   )
#
#   DATAS_DIR = BASE / "datas"
#   CONVODATA_DIR = Path("convodata")
#
#   # startup:
#   init_experimental_files(DATAS_DIR)
#   EXP_FLAGS = load_experimental_flags(DATAS_DIR)
#
#   # usage examples:
#   if is_feature_enabled(EXP_FLAGS, "ARCHIVIST_PASS"):
#       ...
#
#   log_text = read_log_memory(DATAS_DIR, CONVODATA_DIR)
#
#   if can_ai_toggle(EXP_FLAGS, "VOCAB_SELF_MUTATION"):
#       set_feature_flag(
#           DATAS_DIR,
#           feature="VOCAB_SELF_MUTATION",
#           state="on",
#           actor="AI_SELF",
#           reason="Local reversible mutation pathway remains stable."
#       )
#
#   else:
#       propose_feature_toggle(
#           DATAS_DIR,
#           feature="VOCAB_SELF_MUTATION",
#           state="on",
#           reason="Observed stable local mutation behavior across recent sessions."
#       )
#
# ── CONTRIBUTIONS — ChatGPT ──────────────────────────────────────────────────
# 1. Added datas/experimental.txt governance layer with USER / AI_SUGGEST /
#    AI_SELF authority modes.
# 2. Added datas/experimental_journal.txt append-only change log.
# 3. Added read-only log memory helpers so the AI can inspect historical logs
#    for self-optimization reference.
# 4. Added append-only log write helpers so the AI can extend memory without
#    rewriting prior log history.
# 5. Added feature-toggle mutation and proposal helpers that enforce authority
#    rules instead of raw boolean switching.
# 6. Added lightweight suggestion helper for proposing experimental changes
#    based on recent log content.
# 7. Structured the patch as a standalone drop-in module so it can be reviewed
#    and merged into GoddessAPI.py without rewriting the whole runtime.
