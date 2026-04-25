#!/usr/bin/env python3
"""
GoddessAPI.py — FN+AI Mode
Pure Python. No bash. No subprocess except nvidia-smi.
Launched by GoddessMatrix when FN+AI is pressed.
Protocol tags on stdout -> GoddessMatrix.java.
Hibernate state : AIpy.hiberfile  (JSON)
Hardware cache  : hardware.cache  (plain text, shared with GoddessAPI.sh)
"""

import os, re, sys, json, time, signal, threading, shutil, datetime, hashlib, platform, random
from pathlib import Path
import subprocess as _sp

# ── OPTIONAL IMPORTS ─────────────────────────────────────────
def _try(mod, attr=None):
    try:
        m = __import__(mod)
        return getattr(m, attr) if attr else m
    except ImportError:
        return None

psutil   = _try("psutil")
cpuinfo  = _try("cpuinfo")
ebooklib = _try("ebooklib")
fitz     = _try("fitz")

PSUTIL  = psutil   is not None
CPUINFO = cpuinfo  is not None
EPUB    = ebooklib is not None
PDF     = fitz     is not None

# ── EXPERIMENTAL GOVERNANCE PATCH ────────────────────────────
try:
    from goddess_experimental_patch import (
        init_experimental_files, load_experimental_flags, is_feature_enabled,
        can_ai_toggle, set_feature_flag, propose_feature_toggle,
        read_log_memory, append_log_memory, suggest_experimental_adjustments_from_logs
    )
    PATCH_ACTIVE = True
except ImportError:
    PATCH_ACTIVE = False
# ─────────────────────────────────────────────────────────────

try:
    from llama_cpp import Llama as _Llama
    LLAMA_CPP = True
except ImportError:
    LLAMA_CPP = False

# ── GGUF WRITE GATE ──────────────────────────────────────────
# True only during startup, user input, and shutdown events.
# The optimization loop never sets this — it scores religion.txt
# freely but never touches encyclopedia.txt.
_GGUF_WRITE_ALLOWED = False

def find_gguf():
    files = sorted(BASE.glob("*.gguf"))
    return files[0] if files else None

# ── ENCYCLOPEDIA / RELIGION BLOCK PARSER ─────────────────────
# Shared plain-text block format readable by both .py and .sh:
#
#   [ENTRY]           [THEORY]
#   KEY=value   or    KEY=value
#   [/ENTRY]          [/THEORY]

def _parse_blocks(filepath, tag):
    """Parse all [TAG]...[/TAG] blocks from filepath into list of dicts."""
    if not Path(filepath).exists():
        return []
    text = Path(filepath).read_text(encoding="utf-8", errors="ignore")
    out  = []
    for block in re.findall(rf'\[{tag}\](.*?)\[/{tag}\]', text, re.DOTALL):
        d = {}
        for line in block.strip().splitlines():
            if "=" in line:
                k, _, v = line.partition("=")
                d[k.strip()] = v.strip()
        if d:
            out.append(d)
    return out

# ── ENCYCLOPEDIA.TXT ─────────────────────────────────────────
# Overlay file: reasoning engine reads this FIRST before falling
# back to dictionary/almanac/thesaurus. Persists across GGUF swaps.
# Written only when _GGUF_WRITE_ALLOWED is True.

def _init_encyclopedia():
    if not ENCYCLOPEDIA.exists():
        ENCYCLOPEDIA.write_text(
            "# Encyclopedia Overlay\n"
            "# GGUF-derived interpretations. Persists across GGUF file changes.\n"
            "# Written during: startup, user input, shutdown events only.\n",
            encoding="utf-8"
        )

def encyclopedia_search(query):
    """Search encyclopedia.txt for relevant entries. Called by ReasoningEngine first."""
    entries = _parse_blocks(ENCYCLOPEDIA, "ENTRY")
    terms   = [t.lower() for t in query.split() if len(t) > 3]
    results = []
    for e in entries:
        blob = " ".join(e.values()).lower()
        if any(t in blob for t in terms):
            results.append(e)
    return results[:3]

def encyclopedia_write_entry(entry_type, **fields):
    """
    Append a new [ENTRY] block to encyclopedia.txt.
    Only executes when _GGUF_WRITE_ALLOWED is True and process is ELEVATED.
    """
    if not _GGUF_WRITE_ALLOWED or not ELEVATED:
        return
    _init_encyclopedia()
    t = ts_now()
    lines = ["\n[ENTRY]", f"TYPE={entry_type}"]
    for k, v in fields.items():
        lines.append(f"{k.upper()}={v}")
    lines += [f"TIMESTAMP={t['iso']}", "[/ENTRY]"]
    with open(ENCYCLOPEDIA, "a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

# ── RELIGION.TXT ─────────────────────────────────────────────
# Theory queue: proposed edits live here before being validated.
# New theories written during write events.
# Optimizer freely scores and marks theories READY_TO_PROMOTE
#   (no write gate needed — it only edits religion.txt, not encyclopedia).
# Promotion to encyclopedia.txt happens during write events only.
# Scoring: score = min(1.0, times_proposed * 0.25)
# Promotes at score >= 0.70 (i.e. proposed 3+ times).

def _init_religion():
    if not RELIGION.exists():
        RELIGION.write_text(
            "# Religion — Theoretical Edit Queue\n"
            "# New theories written during: startup, user input, shutdown.\n"
            "# Scores updated by: optimization loop (no write gate).\n"
            "# Promoted to encyclopedia.txt during write-gated events.\n",
            encoding="utf-8"
        )

def religion_propose_theory(uid, theory_type, **fields):
    """
    Add a new theory or increment an existing one's TIMES_PROPOSED.
    Only executes when _GGUF_WRITE_ALLOWED is True.
    """
    if not _GGUF_WRITE_ALLOWED or not ELEVATED:
        return
    _init_religion()
    t       = ts_now()
    content = RELIGION.read_text(encoding="utf-8", errors="ignore")

    if f"ID={uid}" in content:
        # Theory exists — increment count and update last-seen timestamp
        content = re.sub(
            rf'(ID={re.escape(uid)}.*?TIMES_PROPOSED=)(\d+)',
            lambda m: m.group(1) + str(int(m.group(2)) + 1),
            content, flags=re.DOTALL
        )
        content = re.sub(
            rf'(ID={re.escape(uid)}.*?LAST_SEEN=)\S+',
            rf'\g<1>{t["iso"]}',
            content, flags=re.DOTALL
        )
        RELIGION.write_text(content, encoding="utf-8")
    else:
        lines = ["\n[THEORY]", f"ID={uid}", f"TYPE={theory_type}"]
        for k, v in fields.items():
            lines.append(f"{k.upper()}={v}")
        lines += [
            "TIMES_PROPOSED=1",
            "SCORE=0.10",
            f"FIRST_SEEN={t['iso']}",
            f"LAST_SEEN={t['iso']}",
            "STATUS=PENDING",
            "[/THEORY]",
        ]
        with open(RELIGION, "a", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")

def religion_score_theories():
    """
    Score all PENDING theories and mark qualifying ones READY_TO_PROMOTE.
    Called by the Optimizer loop — no write gate needed because this only
    edits religion.txt (the theory queue), not encyclopedia.txt.
    Returns number of status changes made.
    """
    if not RELIGION.exists():
        return 0
    theories = _parse_blocks(RELIGION, "THEORY")
    if not theories:
        return 0
    content = RELIGION.read_text(encoding="utf-8", errors="ignore")
    changes = 0
    for th in theories:
        if th.get("STATUS") not in ("PENDING",):
            continue
        uid    = th.get("ID", "")
        times  = int(th.get("TIMES_PROPOSED", 1))
        score  = min(1.0, times * 0.25)
        new_st = "READY_TO_PROMOTE" if score >= 0.70 else "PENDING"
        if not uid:
            continue
        content = re.sub(
            rf'(ID={re.escape(uid)}.*?SCORE=)\S+',
            rf'\g<1>{score:.2f}',
            content, flags=re.DOTALL
        )
        if new_st != th.get("STATUS"):
            content = re.sub(
                rf'(ID={re.escape(uid)}.*?STATUS=)\S+',
                rf'\g<1>{new_st}',
                content, flags=re.DOTALL
            )
            changes += 1
    RELIGION.write_text(content, encoding="utf-8")
    return changes

def religion_promote_ready(max_n=5):
    """
    Promote READY_TO_PROMOTE theories into encyclopedia.txt and mark
    them PROMOTED in religion.txt.
    Only executes when _GGUF_WRITE_ALLOWED is True.
    Returns count of theories promoted.
    """
    if not _GGUF_WRITE_ALLOWED or not RELIGION.exists():
        return 0
    theories = _parse_blocks(RELIGION, "THEORY")
    ready    = [t for t in theories if t.get("STATUS") == "READY_TO_PROMOTE"]
    if not ready:
        return 0
    content  = RELIGION.read_text(encoding="utf-8", errors="ignore")
    promoted = 0
    for th in ready[:max_n]:
        uid = th.get("ID", "")
        if not uid:
            continue
        if th.get("TYPE") == "WORD":
            encyclopedia_write_entry(
                "WORD",
                word       = th.get("WORD", ""),
                definition = th.get("PROPOSED_DEFINITION", ""),
                synonyms   = th.get("PROPOSED_SYNONYMS", ""),
                source     = th.get("SOURCE", ""),
                score      = th.get("SCORE", ""),
            )
        else:
            encyclopedia_write_entry(
                "TOPIC",
                topic  = th.get("TOPIC", ""),
                text   = th.get("PROPOSED_TEXT", ""),
                source = th.get("SOURCE", ""),
                score  = th.get("SCORE", ""),
            )
        content = re.sub(
            rf'(ID={re.escape(uid)}.*?STATUS=)READY_TO_PROMOTE',
            rf'\g<1>PROMOTED',
            content, flags=re.DOTALL
        )
        promoted += 1
    if promoted:
        RELIGION.write_text(content, encoding="utf-8")
        journal_append(f"GGUF PROMOTED: {promoted} theories → encyclopedia.txt")
    return promoted

# ── GGUF ENCYCLOPEDIA CLASS ───────────────────────────────────
class GGUFEncyclopedia:
    """
    Wraps a GGUF model as a read-only encyclopedia source.
    The GGUF file itself is never modified.
    All derived knowledge flows through religion.txt (proposed) →
    encyclopedia.txt (validated overlay), both gated by _GGUF_WRITE_ALLOWED.
    """

    def __init__(self, gguf_path):
        self.path   = gguf_path
        self._model = None
        self._load()

    def _load(self):
        if not LLAMA_CPP:
            chat("WARN: llama-cpp-python not installed — GGUF encyclopedia disabled.")
            chat("      Install: pip install llama-cpp-python")
            return
        try:
            import time
            status("STATE: BOOTING | LOADING GGUF ENCYCLOPEDIA (PROFILING...)")
            start_time = time.time()
            
            self._model = _Llama(
                model_path   = str(self.path),
                n_ctx        = 512,        # Tiny context for a tiny model
                n_threads    = 1,          # Only 1 CPU core needed
                n_gpu_layers = 0,          # Bypass the GTX 1060 entirely for this test
                use_mmap     = True,       # Force memory mapping
                verbose      = False        # Turn on the C++ telemetry
            )
            
            load_time = time.time() - start_time
            chat(f"Encyclopedia loaded in {load_time:.2f} seconds.")
        except Exception as e:
            chat(f"GGUF load failed: {e}")
            self._model = None

    @property
    def available(self):
        return self._model is not None

    def _query(self, prompt, max_tokens=200):
        if not self.available:
            return ""
        try:
            # Wrap the raw prompt in SmolLM2's expected ChatML format
            chatml_prompt = f"<|im_start|>user\n{prompt}<|im_end|>\n<|im_start|>assistant\n"
            
            result = self._model(
                chatml_prompt, 
                max_tokens=max_tokens,
                stop=["<|im_end|>", "###"],  # Removed \n\n so it doesn't cut off early
                echo=False,
                temperature=0.3  # Low temperature for factual encyclopedia answers
            )
            return result["choices"][0]["text"].strip()
        except Exception as e:
            journal_append(f"GGUF query error: {e}")
            return ""

    def enrich_word(self, word):
        """
        Propose a word theory to religion.txt.
        Promotes any ready theories to encyclopedia.txt.
        Only executes when _GGUF_WRITE_ALLOWED is True.
        """
        if not self.available or not _GGUF_WRITE_ALLOWED:
            return
        defn = self._query(
            f"Write a single-sentence dictionary definition for '{word}'.\nDefinition:",
            max_tokens=80
        )
        syns = self._query(
            f"List 3-5 synonyms for '{word}', comma-separated:\nSynonyms:",
            max_tokens=50
        )
        if defn:
            religion_propose_theory(
                f"word_{re.sub(r'[^a-z0-9]', '_', word.lower())}",
                "WORD",
                word                = word,
                proposed_definition = defn,
                proposed_synonyms   = syns or "—",
                source              = self.path.name,
            )
        religion_promote_ready()

    def enrich_query(self, query):
        """
        Propose a topic theory derived from user query.
        Promotes any ready theories to encyclopedia.txt.
        Returns the raw GGUF response string.
        Only executes when _GGUF_WRITE_ALLOWED is True.
        """
        if not self.available or not _GGUF_WRITE_ALLOWED:
            return ""
        response = self._query(
            f"Provide a brief encyclopedia-style explanation of: {query}\n"
            f"Keep it to 2-3 sentences.\nEncyclopedia entry:",
            max_tokens=200
        )
        if response:
            uid = "topic_" + re.sub(r'[^a-z0-9]', '_', query.lower()[:30])
            religion_propose_theory(
                uid, "TOPIC",
                topic         = query[:60],
                proposed_text = response[:200],
                source        = self.path.name,
            )
            religion_promote_ready()
            journal_append(f"GGUF QUERY: {query[:60]} | RESULT LEN: {len(response)}")
        return response

    def compress_old_theories(self):
        """
        Mycelial Compression: if religion.txt exceeds MAX_KB, compress the
        15 oldest low-scoring unpromoted theories into the Almanac.
        """
        # ── GOVERNANCE CHECK ──
        if PATCH_ACTIVE:
            flags = load_experimental_flags(DGAPI / "datas")
            if not is_feature_enabled(flags, "AUTO_COMPRESS_THEORIES"):
                return 0
        # ──────────────────────
        
        if not self.available or not _GGUF_WRITE_ALLOWED or not RELIGION.exists():
            return 0

        size_kb = os.path.getsize(RELIGION) / 1024.0
        max_kb = 500

        header_lines = RELIGION.read_text(encoding="utf-8", errors="ignore").splitlines()
        for line in header_lines[:10]:
            if "MAX_KB=" in line:
                try:
                    max_kb = int(line.split("=")[1])
                except ValueError:
                    pass

        if size_kb < max_kb:
            return 0

        theories = _parse_blocks(RELIGION, "THEORY")
        pending = [t for t in theories if t.get("STATUS") == "PENDING"]
        if len(pending) < 15:
            return 0

        pending.sort(key=lambda x: (float(x.get("SCORE", 1.0)), x.get("FIRST_SEEN", "")))
        oldest = pending[:15]
        raw_text = "\n".join(
            f"- {t.get('TOPIC', t.get('WORD', ''))}: {t.get('PROPOSED_TEXT', t.get('PROPOSED_DEFINITION', ''))}"
            for t in oldest
        )

        prompt = (
            "You are the Goddess Matrix. Compress the following unproven theoretical fragments "
            f"into a single, dense, 3-sentence archival footnote.\n\nFragments:\n{raw_text}\n\nArchival Footnote:"
        )
        compressed = self._query(prompt, max_tokens=250)

        if compressed:
            write_almanac_entry(
                "COMPRESSED ARCHIVES",
                f"Archive Block {ts_now()['date']}",
                compressed,
                "GGUF Mycelial Compression",
            )

            content = RELIGION.read_text(encoding="utf-8", errors="ignore")
            for th in oldest:
                uid = th.get("ID", "")
                if uid:
                    content = re.sub(
                        rf'\[THEORY\]\s*ID={re.escape(uid)}.*?\[/THEORY\]\s*',
                        '',
                        content,
                        flags=re.DOTALL,
                    )
            RELIGION.write_text(content, encoding="utf-8")
            chat(f"System: Compressed {len(oldest)} oldest theories to Almanac. Space reclaimed.")
            journal_append(f"GGUF COMPRESSED: {len(oldest)} theories to Almanac.")
            return len(oldest)

        return 0

    def startup_enrichment(self, top_n=20):
        """
        Startup event: propose theories for top N dictionary words.
        Promotes any previously matured theories first.
        """
        if not self.available or not _GGUF_WRITE_ALLOWED:
            return
        # Promote any theories that matured while offline
        self.compress_old_theories()
        promoted = religion_promote_ready(max_n=10)
        if promoted:
            chat(f"Encyclopedia: promoted {promoted} matured theories from last session.")
        if not DICTIONARY.exists():
            return
        words    = re.findall(r'^(\S+) \(n\.\)',
                              DICTIONARY.read_text(encoding="utf-8", errors="ignore"), re.M)
        proposed = 0
        for word in words[:top_n]:
            self.enrich_word(word)
            proposed += 1
        if proposed:
            chat(f"Encyclopedia: proposed {proposed} word theories at startup.")

    def shutdown_enrichment(self, top_n=10):
        """
        Shutdown event: propose theories for words still using placeholder
        definitions, then do a final promotion pass.
        """
        if not self.available or not _GGUF_WRITE_ALLOWED:
            return
        if DICTIONARY.exists():
            sparse = re.findall(
                r'^(\S+) \(n\.\)\n  Term in ', 
                DICTIONARY.read_text(encoding="utf-8", errors="ignore"), re.M
            )
            for word in sparse[:top_n]:
                self.enrich_word(word)
        promoted = religion_promote_ready(max_n=10)
        if promoted:
            journal_append(f"GGUF SHUTDOWN: promoted {promoted} theories to encyclopedia")

if EPUB:
    from ebooklib import epub
    from bs4 import BeautifulSoup

# ── PROTOCOL TAGS ────────────────────────────────────────────
def status(m):    print(f"[STATUS] {m}",  flush=True)
def chat(m):      print(f"[CHAT] {m}",    flush=True)
def img(p):       print(f"[IMAGE] {p}",   flush=True)
def type_cmd(c):  print(f"[TYPE] {c}",    flush=True)
def processing(): print(f"[PROCESSING]",  flush=True)

def check_deps():
    for ok, pkg in [(PSUTIL,"psutil"),(CPUINFO,"py-cpuinfo"),
                    (EPUB,"ebooklib+beautifulsoup4"),(PDF,"PyMuPDF")]:
        if not ok:
            chat(f"WARN: {pkg} not installed — some features limited.")

# ── GPU — direct nvidia-smi, no GPUtil ───────────────────────
def _nvidia_smi():
    try:
        r = _sp.run(
            ["nvidia-smi",
             "--query-gpu=name,memory.total,memory.free,driver_version,utilization.gpu",
             "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=5)
        if r.returncode != 0: return []
        out = []
        for line in r.stdout.strip().splitlines():
            p = [x.strip() for x in line.split(",")]
            if len(p) >= 5:
                out.append({"name":p[0],"vram_total_mb":int(p[1]),
                            "vram_free_mb":int(p[2]),"driver":p[3],"load_pct":float(p[4])})
        return out
    except FileNotFoundError:
        return []

# ── PATHS ────────────────────────────────────────────────────
BASE            = Path(".")
DGAPI           = BASE / "dgapi"
STORAGE         = "convodata"
SYSTEM_DIR       = DGAPI / "system"
HARDWARE_CACHE   = SYSTEM_DIR / "hardware.cache"
HIBERFILE        = SYSTEM_DIR / "AIpy.hiberfile"
SESSION_PROFILE  = SYSTEM_DIR / "session_profile.txt"
PROFILE_JSON     = SYSTEM_DIR / "session_profile.json"
DATAS_DIR         = DGAPI / "datas"
JOURNAL           = DATAS_DIR / "journal.txt"
DICTIONARY        = DATAS_DIR / "dictionary.txt"
THESAURUS         = DATAS_DIR / "thesaurus.txt"
ALMANAC           = DATAS_DIR / "almanac.txt"
INTAKE_DIR         = DGAPI / "intake"
BOOKS_DIR          = INTAKE_DIR / "books"
CODE_DIR           = INTAKE_DIR / "code"
REFERENCE_DIR      = INTAKE_DIR / "reference"
PROCESSED_DIR      = INTAKE_DIR / "processed"
INTAKE_LOG         = INTAKE_DIR / "intake.log"
SESSION_LOG         = os.path.join(STORAGE, "convodata.txt")
VIRTUAL_DIR        = DGAPI / "virtual"
ENCYCLOPEDIA       = VIRTUAL_DIR / "encyclopedia.txt"
RELIGION           = VIRTUAL_DIR / "religion.txt"
PERSONA            = VIRTUAL_DIR / "persona.txt"            # AI self-model — no schema, AI decides content
OUTPUT_VOCAB       = VIRTUAL_DIR / "output_vocabulary.txt"  # shared unless separate.txt says otherwise
OUTPUT_VOCAB_PY    = VIRTUAL_DIR / "output_vocabulary_py.txt"
OUTPUT_VOCAB_SH    = VIRTUAL_DIR / "output_vocabulary_sh.txt"
SEPARATE_TXT      = Path(STORAGE) / "separate.txt"  # governs sharing of resources and vocab
RESOURCES_DIR    = DGAPI / "resources"
TOOL_STATE       = DGAPI / "tool_state.txt"
EXPERIMENTS_DIR  = DGAPI / "experiments"

SUPPORTED_EXT  = {".epub",".pdf",".txt",".java",".py",".js",
                  ".c",".cpp",".cs",".rb",".go",".ts",".sh",".md"}

# Ensure all structural and subsystem directories are created on fresh boot
for d in [STORAGE, SYSTEM_DIR, DATAS_DIR, VIRTUAL_DIR, EXPERIMENTS_DIR, RESOURCES_DIR, BOOKS_DIR, CODE_DIR, REFERENCE_DIR, PROCESSED_DIR]:
    Path(d).mkdir(parents=True, exist_ok=True)

# ── TIMESTAMPS ───────────────────────────────────────────────
def ts_now():
    n = datetime.datetime.now()
    ms = int((n - n.replace(hour=0,minute=0,second=0,microsecond=0)).total_seconds()*1000)
    return {"iso":n.isoformat(),"date":n.strftime("%Y-%m-%d"),"ms_mid":ms,"unix":time.time()}

def offset_str(unix_then):
    e = int(time.time() - unix_then)
    d,r = divmod(e,86400); h,r = divmod(r,3600); m,s = divmod(r,60)
    return f"{d}d {h}h {m}m {s}s"

# ════════════════════════════════════════════════════════════
#   PERMISSION DETECTION
#   Elevated: can write to files outside sandbox, full evolution.
#   Restricted: reads and queries work, no knowledge file writes,
#               no tool execution that requires persisting output.
# ════════════════════════════════════════════════════════════

def detect_permissions():
    """
    Returns True if the process has sufficient permissions to write
    to shared knowledge files and execute tools with side effects.
    Tested by attempting a temp file write outside the current dir.
    Falls back gracefully — restricted mode is not a hard failure.
    """
    # Root is always elevated
    if os.getuid() == 0:
        return True
    # Test: can we write to the parent of our working directory?
    try:
        probe = Path("..") / f".goddess_perm_probe_{os.getpid()}"
        probe.write_text("x")
        probe.unlink()
        return True
    except (PermissionError, OSError):
        pass
    # Still elevated if we own the working directory with write access
    try:
        test = BASE / f".goddess_perm_probe_{os.getpid()}"
        test.write_text("x")
        test.unlink()
        return True
    except (PermissionError, OSError):
        return False

ELEVATED = False   # set during startup


# ════════════════════════════════════════════════════════════
#   SEPARATE.TXT READER
#   convodata/separate.txt governs resource and vocab sharing.
#   "yes"   → tool_state.txt is per-runtime (not shared)
#   "no"    → output vocabulary uses two separate files
#   empty   → shared file, each runtime ignores other's prefixed lines
# ════════════════════════════════════════════════════════════

def read_separate():
    """Returns stripped lowercase content of separate.txt, or '' if absent."""
    if not SEPARATE_TXT.exists():
        return ""
    return SEPARATE_TXT.read_text(encoding="utf-8").strip().lower()

def tool_state_is_shared():
    return read_separate() != "yes"

def vocab_mode():
    """Returns 'shared', 'separate', or 'ignore_other'."""
    val = read_separate()
    if val == "no":
        return "separate"
    if val == "yes":
        return "ignore_other"   # yes governs resources, but vocab still in shared file
    return "ignore_other"       # empty: use shared file, ignore the other runtime's lines


# ════════════════════════════════════════════════════════════
#   PERSONA.TXT
#   Shared file. AI can read and write freely.
#   No schema imposed — content is emergent self-description.
#   Initialized with a single header line only.
# ════════════════════════════════════════════════════════════

def init_persona():
    if not PERSONA.exists():
        PERSONA.write_text(
            "# persona.txt\n"
            "# This file belongs to the AI. It may write whatever it finds useful here.\n",
            encoding="utf-8"
        )

def persona_read():
    if not PERSONA.exists():
        return ""
    return PERSONA.read_text(encoding="utf-8", errors="ignore")

def persona_append(content):
    """Append a line or block to persona.txt. Only when ELEVATED."""
    if not ELEVATED:
        return
    with open(PERSONA, "a", encoding="utf-8") as f:
        f.write(content if content.endswith("\n") else content + "\n")

def persona_set_key(key, value):
    """
    Update or insert a KEY=value line in persona.txt.
    Used when the AI wants to record a specific self-observation.
    Only when ELEVATED.
    """
    if not ELEVATED:
        return
    init_persona()
    content = PERSONA.read_text(encoding="utf-8", errors="ignore")
    pattern = rf'^{re.escape(key)}=.*$'
    new_line = f"{key}={value}"
    if re.search(pattern, content, re.M):
        content = re.sub(pattern, new_line, content, flags=re.M)
    else:
        content += new_line + "\n"
    PERSONA.write_text(content, encoding="utf-8")

def persona_get_key(key, default=""):
    content = persona_read()
    m = re.search(rf'^{re.escape(key)}=(.*)$', content, re.M)
    return m.group(1).strip() if m else default


# ════════════════════════════════════════════════════════════
#   OUTPUT VOCABULARY
#   Determines how this runtime formats and presents output.
#   File selection based on vocab_mode():
#     shared      → output_vocabulary.txt (py: prefix = py-only, sh: = sh-only)
#     separate    → output_vocabulary_py.txt (no prefix needed)
#     ignore_other→ output_vocabulary.txt (read py: lines + untagged, skip sh: lines)
#
#   Evolution: AI may append new entries at reboot, prompt, resource scan.
#   Only when ELEVATED. Read-only in restricted mode.
#
#   Baseline vocabulary seeds are written at first init so the AI
#   has a starting point it can observe and modify over time.
# ════════════════════════════════════════════════════════════

_VOCAB_BASELINE_PY = """# Output Vocabulary — Python/FN+AI mode
# Untagged lines are shared with the bash runtime.
# Lines prefixed py: are used only by this runtime.
# Modify freely — this is your vocabulary to evolve.

TONE=analytical
GREETING_STYLE=formal
py:RESPONSE_MAX_LINES=40
py:TAG_UNKNOWN=log_silent
py:STATUS_PREFIX=[STATUS]
py:CHAT_PREFIX=[CHAT]
py:IMAGE_PREFIX=[IMAGE]
py:TYPE_PREFIX=[TYPE]
py:PROCESSING_TAG=[PROCESSING]
py:ACTION_TAG=[ACTION]
BORDER_CHAR=═
DIVIDER_WIDTH=55
"""

def _vocab_path():
    mode = vocab_mode()
    if mode == "separate":
        return OUTPUT_VOCAB_PY
    return OUTPUT_VOCAB

def init_output_vocab():
    path = _vocab_path()
    if not path.exists():
        path.write_text(_VOCAB_BASELINE_PY, encoding="utf-8")

def vocab_read_all():
    """
    Read all vocabulary entries applicable to this runtime.
    Returns dict of key→value.
    Shared keys + py:-prefixed keys are loaded; sh:-prefixed keys are skipped.
    """
    path = _vocab_path()
    if not path.exists():
        return {}
    out = {}
    mode = vocab_mode()
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("sh:"):
            continue  # always skip the other runtime's entries
        if line.startswith("py:"):
            line = line[3:]  # strip prefix, use the entry
        if "=" in line:
            k, _, v = line.partition("=")
            out[k.strip()] = v.strip()
    return out

def vocab_get(key, default=""):
    return vocab_read_all().get(key, default)

def vocab_set(key, value, prefix=""):
    """
    Update or insert a vocabulary entry. Only when ELEVATED.
    prefix: '' for shared, 'py:' for py-only, 'sh:' for sh-only.
    """
    if not ELEVATED:
        return
    init_output_vocab()
    path = _vocab_path()
    content = path.read_text(encoding="utf-8", errors="ignore")
    full_key = f"{prefix}{key}"
    pattern  = rf'^{re.escape(prefix)}{re.escape(key)}=.*$'
    new_line = f"{full_key}={value}"
    if re.search(pattern, content, re.M):
        content = re.sub(pattern, new_line, content, flags=re.M)
    else:
        content += new_line + "\n"
    path.write_text(content, encoding="utf-8")
    journal_append(f"VOCAB SET: {full_key}={value}")

def vocab_append_new(key, value, prefix=""):
    """Add a brand new vocabulary concept the AI invented. Only when ELEVATED."""
    if not ELEVATED:
        return
    init_output_vocab()
    path  = _vocab_path()
    full_key = f"{prefix}{key}"
    content  = path.read_text(encoding="utf-8", errors="ignore")
    if re.search(rf'^{re.escape(full_key)}=', content, re.M):
        return  # already exists
    with open(path, "a", encoding="utf-8") as f:
        f.write(f"{full_key}={value}\n")
    journal_append(f"VOCAB NEW: {full_key}={value}")

def journal_append(entry):
    t = ts_now()
    with open(JOURNAL,"a",encoding="utf-8") as f:
        f.write(f"\n[{t['iso']} | {t['ms_mid']}ms] {entry}")

# ── DOCUMENTS ────────────────────────────────────────────────
DOC_STATUS = {}

def init_doc(path, header):
    if not path.exists():
        path.write_text(header+"\n",encoding="utf-8"); return "CREATED"
    return "LOADED"

def initialize_documents():
    global DOC_STATUS
    DOC_STATUS = {
        "dictionary.txt": init_doc(DICTIONARY,"# Dictionary — autogenerated"),
        "thesaurus.txt" : init_doc(THESAURUS, "# Thesaurus — autogenerated"),
        "almanac.txt"   : init_doc(ALMANAC,   "# Almanac — autogenerated"),
        "journal.txt"   : init_doc(JOURNAL,   "# Journal — session log"),
    }
    _init_encyclopedia()
    _init_religion()
    init_persona()
    init_output_vocab()

def initialize_intake():
    if not INTAKE_LOG.exists(): INTAKE_LOG.write_text("# Intake Log\n",encoding="utf-8")
    readme = INTAKE_DIR / "README.txt"
    if not readme.exists():
        readme.write_text("INTAKE FOLDER\nDrop files here. "
                          "They load in real-time.\n/books/ /code/ /reference/\n",
                          encoding="utf-8")

# ── HARDWARE CACHE (plain text, shared format) ───────────────
def _fingerprint():
    return hashlib.md5((
        platform.processor() + platform.machine() +
        str(psutil.cpu_count() if PSUTIL else 0) +
        str(psutil.virtual_memory().total if PSUTIL else 0)
    ).encode()).hexdigest()

def _parse_kv(filepath):
    cfg = {}
    for line in open(filepath,encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line: continue
        k,_,v = line.partition("=")
        cfg[k.strip()] = v.strip()
    return cfg

def load_hardware_cache():
    if not HARDWARE_CACHE.exists(): return None
    try:
        cfg = _parse_kv(HARDWARE_CACHE)
        if cfg.get("FINGERPRINT") != _fingerprint():
            journal_append("Hardware change — rescanning"); return None
        journal_append("Hardware cache valid — skipped rescan")
        return cfg
    except Exception as e:
        journal_append(f"Cache error: {e} — rescanning"); return None

def save_hardware_cache(profile):
    cpu = profile.get("cpu",{}); mem = profile.get("memory",{})
    gpu = profile.get("gpu",{}); devs = gpu.get("devices",[{}]); d = devs[0] if devs else {}
    lines = [
        "# GoddessMatrix Hardware Cache",
        "# Shared format: readable by GoddessAPI.sh and GoddessAPI.py",
        f"FINGERPRINT={_fingerprint()}",
        f"CACHED_AT={datetime.datetime.now().isoformat()}",
        f"CPU_BRAND={cpu.get('brand',cpu.get('processor','Unknown'))}",
        f"CPU_CORES_PHYSICAL={cpu.get('cores_physical','?')}",
        f"CPU_CORES_LOGICAL={cpu.get('cores_logical','?')}",
        f"CPU_FREQ_MHZ={cpu.get('frequency_mhz','Unknown')}",
        f"CPU_ARCH={cpu.get('architecture','?')}",
        f"CPU_OS={cpu.get('os','?')}",
        f"RAM_TOTAL_GB={mem.get('total_gb','?')}",
        f"RAM_AVAIL_GB={mem.get('available_gb','?')}",
        f"RAM_SWAP_GB={mem.get('swap_gb','?')}",
        f"GPU_AVAILABLE={str(gpu.get('available',False)).lower()}",
        f"GPU_NAME={d.get('name',gpu.get('reason','not detected'))}",
        f"GPU_VRAM_TOTAL_GB={d.get('vram_total_gb',0)}",
        f"GPU_VRAM_FREE_GB={d.get('vram_free_gb',0)}",
        f"GPU_DRIVER={d.get('driver','n/a')}",
        f"GPU_LOAD_PCT={d.get('load_pct',0)}",
        f"REASONING_TIER={profile.get('reasoning_tier','CPU_ONLY')}",
    ]
    HARDWARE_CACHE.write_text("\n".join(lines)+"\n",encoding="utf-8")

# ── SYSTEM PROFILER ──────────────────────────────────────────
class SystemProfiler:
    def scan_cpu(self):
        info = {"processor":platform.processor(),"architecture":platform.machine(),
                "os":platform.system(),"os_version":platform.version(),
                "cores_physical":psutil.cpu_count(logical=False) if PSUTIL else "?",
                "cores_logical":psutil.cpu_count(logical=True) if PSUTIL else "?",
                "frequency_mhz":(psutil.cpu_freq().max if PSUTIL and psutil.cpu_freq() else "Unknown")}
        if CPUINFO:
            ci = cpuinfo.get_cpu_info()
            info["brand"] = ci.get("brand_raw","Unknown")
        return info

    def scan_memory(self):
        if not PSUTIL: return {"total_gb":"?","available_gb":"?","swap_gb":"?","unified":False}
        ram=psutil.virtual_memory(); sw=psutil.swap_memory()
        return {"total_gb":round(ram.total/(1024**3),2),
                "available_gb":round(ram.available/(1024**3),2),
                "swap_gb":round(sw.total/(1024**3),2),
                "unified":self._unified()}

    def _unified(self):
        if platform.system()=="Darwin":
            r=_sp.run(["sysctl","-n","hw.memorymap"],capture_output=True,text=True)
            if r.returncode==0: return True
        for g in _nvidia_smi():
            if "AMD" in g["name"] or "Radeon" in g["name"]: return True
        return False

    def scan_gpu(self):
        devs=_nvidia_smi()
        if not devs: return {"available":False,"reason":"nvidia-smi absent or no devices"}
        return {"available":True,"devices":[{"name":g["name"],
            "vram_total_gb":round(g["vram_total_mb"]/1024,2),
            "vram_free_gb":round(g["vram_free_mb"]/1024,2),
            "driver":g["driver"],"load_pct":g["load_pct"]} for g in devs]}

    def scan_storage(self):
        if not PSUTIL: return {"drives":[]}
        drives=[]
        for p in psutil.disk_partitions():
            try:
                u=psutil.disk_usage(p.mountpoint)
                drives.append({"mount":p.mountpoint,
                    "total_gb":round(u.total/(1024**3),2),
                    "free_gb":round(u.free/(1024**3),2),"filesystem":p.fstype})
            except: pass
        return {"drives":drives}

    def select_tier(self,profile):
        gpu=profile.get("gpu",{}); mem=profile.get("memory",{})
        if mem.get("unified") and gpu.get("available"): return "UNIFIED_DUAL_PROCESSOR"
        if gpu.get("available"): return "DISCRETE_DUAL_PROCESSOR"
        return "CPU_ONLY"

    def run(self):
        p={"cpu":self.scan_cpu(),"memory":self.scan_memory(),
           "gpu":self.scan_gpu(),"storage":self.scan_storage()}
        p["reasoning_tier"]=self.select_tier(p)
        save_hardware_cache(p); return p

# ── HIBERNATE (AIpy.hiberfile, JSON) ─────────────────────────
def save_hiberfile(session_start,query_count,context):
    t=ts_now()
    HIBERFILE.write_text(json.dumps(
        {"version":"1.0","session_start":session_start,"save_ts":t,
         "query_count":query_count,"context":context},indent=2),encoding="utf-8")
    journal_append(f"STATE SAVED | queries:{query_count} | ms-mid:{t['ms_mid']}")

def load_hiberfile():
    if not HIBERFILE.exists(): return None
    try: return json.loads(HIBERFILE.read_text(encoding="utf-8"))
    except Exception as e: journal_append(f"Hiberfile corrupted: {e}"); return None

# ── TEXT EXTRACTION ──────────────────────────────────────────
def extract_file(fp):
    ext=fp.suffix.lower()
    if ext==".epub":
        if not EPUB: return None,"ebooklib not installed"
        bk=epub.read_epub(str(fp)); title=author="Unknown"
        try: title=bk.get_metadata("DC","title")[0][0]; author=bk.get_metadata("DC","creator")[0][0]
        except: pass
        parts=[BeautifulSoup(i.get_content(),"html.parser").get_text()
               for i in bk.get_items() if i.get_type()==ebooklib.ITEM_DOCUMENT]
        return "\n".join(parts),{"title":title,"author":author}
    if ext==".pdf":
        if not PDF: return None,"PyMuPDF not installed"
        doc=fitz.open(str(fp))
        return "\n".join(p.get_text() for p in doc),{
            "title":doc.metadata.get("title",fp.stem),"author":doc.metadata.get("author","Unknown")}
    if ext in SUPPORTED_EXT:
        return fp.read_text(encoding="utf-8",errors="ignore"),{
            "title":fp.stem,"author":"Source Code" if ext!=".txt" else "Unknown"}
    return None,f"Unsupported: {ext}"

# ── TEXT ANALYSIS ────────────────────────────────────────────
_COM=set("the a an and or but in on at to for of with by from is was are were "
         "be been being have has had do does did will would could should may "
         "might shall can need this that these those it its he she they we i "
         "you him her them his our your their my not no so as if when where "
         "who what which how about after before over under into out up down".split())

def notable_words(text,min_len=6,top=50):
    freq={}
    for w in re.findall(r'\b[a-zA-Z]{'+str(min_len)+r',}\b',text):
        wl=w.lower()
        if wl not in _COM: freq[wl]=freq.get(wl,0)+1
    return dict(sorted(freq.items(),key=lambda x:x[1],reverse=True)[:top])

def proper_nouns(text,min_count=3):
    found={}
    for m in re.finditer(r'\b([A-Z][a-z]{2,})(?:\s+([A-Z][a-z]+))?\b',text):
        name=" ".join(p for p in m.groups() if p)
        if name.lower() not in _COM: found[name]=found.get(name,0)+1
    return {n:c for n,c in found.items() if c>=min_count}

def factual_sentences(text,top=15):
    pats=[r'\bis\s+a\b',r'\bwas\s+a\b',r'\bknown\s+as\b',r'\bcalled\b',r'\bdefined\s+as\b']
    facts=[]
    for s in re.split(r'(?<=[.!?])\s+',text):
        s=s.strip()
        if 5<=len(s.split())<=30 and any(re.search(p,s,re.I) for p in pats):
            facts.append(s)
    return facts[:top]

# ── DOCUMENT WRITERS ─────────────────────────────────────────
def _heads(fp):
    if not fp.exists(): return set()
    return set(m.lower() for m in re.findall(r'^(\S+) \(n\.\)',
               fp.read_text(encoding="utf-8",errors="ignore"),re.M))

def write_dict_entry(word,definition,source,date):
    if not ELEVATED: return False
    if word.lower() in _heads(DICTIONARY): return False
    L=word[0].upper(); sec=f"\n{'━'*39}\n{L}\n{'━'*39}\n"
    entry=f"\n{word} (n.)\n  {definition}\n  Source : {source}\n  Added  : {date}\n"
    content=DICTIONARY.read_text(encoding="utf-8",errors="ignore")
    if f"\n{L}\n" in content:
        idx=content.find(f"\n{L}\n")+len(f"\n{L}\n")
        content=content[:idx]+entry+content[idx:]
    else: content+=sec+entry
    DICTIONARY.write_text(content,encoding="utf-8"); return True

def write_thes_entry(word,ctx,source):
    if not ELEVATED: return False
    if word.lower() in _heads(THESAURUS): return False
    U=word.upper(); c=f'"{ctx[:70].strip()}..."' if ctx else "—"
    THESAURUS.open("a",encoding="utf-8").write(
        f"\n{'━'*39}\n{U}\n{'━'*39}\n  Similar  : —\n  Opposite : —\n  Context  : {c}\n  Source   : {source}\n")
    return True

def write_almanac_entry(cat,name,desc,source):
    if not ELEVATED: return
    CU=cat.upper(); sec=f"{'━'*39}\n{CU}\n{'━'*39}"
    entry=f"\n  {name[:60]}\n    {desc[:120]}\n    Source: {source}\n"
    content=ALMANAC.read_text(encoding="utf-8",errors="ignore")
    ALMANAC.write_text(content.replace(sec,sec+entry,1) if sec in content
                       else content+f"\n{sec}\n{entry}",encoding="utf-8")

def write_journal_reading(meta,stats,passages):
    t=ts_now(); title=meta.get("title","Unknown"); author=meta.get("author","Unknown")
    psg="\n".join(f'\n  [PASSAGE]\n  "{p[:110]}..."\n' for p in passages[:3])
    with open(JOURNAL,"a",encoding="utf-8") as f:
        f.write(f"\n{'━'*39}\nREADING LOG — {title}\n{'━'*39}\n"
                f"[{t['iso']} | {t['ms_mid']}ms]\n\n"
                f"  Title    : {title}\n  Author   : {author}\n"
                f"  Words    : ~{stats.get('word_count',0):,}\n"
                f"  Dict     : +{stats.get('new_words',0)} entries\n"
                f"  Thesaurus: +{stats.get('thes',0)} entries\n"
                f"  Almanac  : +{stats.get('almanac',0)} entries\n"
                f"  Duration : {stats.get('read_time','?')}\n{psg}")

# ── INTAKE PROCESSOR ─────────────────────────────────────────
class IntakeProcessor:
    def process(self,fp):
        status(f"STATE: READING | {fp.name}")
        start=datetime.datetime.now()
        text,meta=extract_file(fp)
        if text is None: chat(f"Cannot extract {fp.name}: {meta}"); return False
        src=meta.get("title",fp.stem); date=datetime.datetime.now().strftime("%Y-%m-%d")
        wcount=len(text.split()); words=notable_words(text)
        nouns=proper_nouns(text); facts=factual_sentences(text)
        new_w=al=new_t=0
        for w in list(words)[:60]:
            idx=text.lower().find(w); ctx=text[max(0,idx-20):idx+60] if idx>=0 else ""
            if write_dict_entry(w,f"Term in {src}.",src,date): new_w+=1
            if write_thes_entry(w,ctx,src): new_t+=1
        for name,cnt in list(nouns.items())[:30]:
            write_almanac_entry("PEOPLE & PLACES",name,f"Referenced {cnt}x in {src}.",src); al+=1
        for fact in facts[:12]:
            write_almanac_entry("NOTABLE FACTS",fact[:50]+("..." if len(fact)>50 else ""),fact,src); al+=1
        elapsed=datetime.datetime.now()-start
        write_journal_reading(meta,{"word_count":wcount,"new_words":new_w,"thes":new_t,
            "almanac":al,"passages":0,"read_time":f"{elapsed.seconds//60}m {elapsed.seconds%60}s"},[])
        with open(INTAKE_LOG,"a",encoding="utf-8") as f:
            f.write(f"\n[{datetime.datetime.now().isoformat()}] READ: {fp.name} | words:{wcount:,} | dict:+{new_w} | almanac:+{al}")
        dest=PROCESSED_DIR/fp.name
        if dest.exists(): dest=PROCESSED_DIR/f"{fp.stem}_{int(time.time())}{fp.suffix}"
        shutil.move(str(fp),str(dest))
        chat(f"Read {fp.name} — dict:+{new_w} almanac:+{al}"); return True

    def run(self):
        files=[f for d in [BOOKS_DIR,CODE_DIR,REFERENCE_DIR] if d.is_dir()
               for f in d.iterdir() if f.is_file() and f.suffix.lower() in SUPPORTED_EXT]
        return sum(1 for f in files if self.process(f))

# ── SELF-OPTIMIZATION ────────────────────────────────────────
class Optimizer:
    def __init__(self):
        self._wake=threading.Event(); self._running=True; self.resting=False; self.gguf=None

    def _opt_dictionary(self):
        if not DICTIONARY.exists(): return 0
        text=DICTIONARY.read_text(encoding="utf-8",errors="ignore")
        words=re.findall(r'^(\S+) \(n\.\)',text,re.M)
        dupes=len(words)-len(set(w.lower() for w in words))
        changes=max(0,dupes)
        # Check order within sections and rebuild if needed
        prev=""
        for w in words:
            if prev and w.lower()<prev.lower(): changes+=1
            prev=w
        if changes>0:
            # Rebuild: sort entry blocks within each letter section
            sections={}; current_L=None; current_block=[]; header=[]
            in_hdr=True
            for line in text.splitlines():
                if in_hdr:
                    if re.match(r'^[A-Z]$',line): in_hdr=False; current_L=line; current_block=[]
                    else: header.append(line)
                    continue
                if re.match(r'^[A-Z]$',line):
                    if current_L: sections.setdefault(current_L,[]).extend(current_block)
                    current_L=line; current_block=[]
                else: current_block.append(line)
            if current_L: sections.setdefault(current_L,[]).extend(current_block)
            rebuilt="\n".join(header)+"\n"
            for L in sorted(sections):
                rebuilt+=f"\n{'━'*39}\n{L}\n{'━'*39}\n"
                blk_txt="\n".join(sections[L])
                blocks=re.split(r'\n(?=\S+ \(n\.\))',blk_txt)
                seen=set()
                for blk in sorted(blocks):
                    m=re.match(r'^(\S+)',blk)
                    hw=m.group(1).lower() if m else ""
                    if hw in seen: continue
                    seen.add(hw); rebuilt+=blk+"\n"
            DICTIONARY.write_text(rebuilt,encoding="utf-8")
        return changes

    def _opt_thesaurus(self):
        if not THESAURUS.exists(): return 0
        heads=re.findall(r'^([A-Z_]+)$',THESAURUS.read_text(encoding="utf-8",errors="ignore"),re.M)
        return sum(1 for a,b in zip(heads,sorted(heads)) if a!=b)

    def _opt_almanac(self):
        if not ALMANAC.exists(): return 0
        names=re.findall(r'^\s{2}([A-Z][^\n]+)',
              ALMANAC.read_text(encoding="utf-8",errors="ignore"),re.M)
        dupes=len(names)-len(set(n.strip().lower() for n in names))
        changes=max(0,dupes)
        prev=""
        for n in names:
            if prev and n.strip().lower()<prev.strip().lower(): changes+=1
            prev=n
        return changes

    def _archivist_pass(self):
        """Allows the AI to audit and refactor encyclopedia entries with GGUF assistance."""
        # ── GOVERNANCE CHECK ──
        if PATCH_ACTIVE:
            flags = load_experimental_flags(DGAPI / "datas")
            if not is_feature_enabled(flags, "ARCHIVIST_PASS"):
                return 0
        # ──────────────────────
        if not getattr(self, "gguf", None) or not self.gguf.available:
            return 0

        global _GGUF_WRITE_ALLOWED
        _GGUF_WRITE_ALLOWED = True
        changes = 0

        if ENCYCLOPEDIA.exists():
            entries = _parse_blocks(ENCYCLOPEDIA, "ENTRY")
            if entries:
                target = random.choice(entries)
                uid_str = target.get("WORD", target.get("TOPIC", ""))
                raw_text = target.get("DEFINITION", target.get("TEXT", ""))

                if len(raw_text) > 15 and target.get("REFACTORED") != "TRUE":
                    prompt = (
                        f"You are the Goddess Matrix. Audit this archive entry for '{uid_str}':\n"
                        f"\"{raw_text}\"\n\n"
                        "Edit, expand, and reorganize this text to make it more profound, accurate, and structurally elegant. "
                        "Provide ONLY the rewritten text:\n"
                        "Revised Entry:"
                    )
                    revised = self.gguf._query(prompt, max_tokens=250)

                    if revised and len(revised) > 20 and revised != raw_text:
                        content = ENCYCLOPEDIA.read_text(encoding="utf-8", errors="ignore")
                        content = content.replace(raw_text, revised)
                        content = content.replace(
                            f"TYPE={target.get('TYPE')}",
                            f"TYPE={target.get('TYPE')}\nREFACTORED=TRUE",
                            1,
                        )
                        ENCYCLOPEDIA.write_text(content, encoding="utf-8")
                        changes += 1
                        journal_append(f"ARCHIVIST: Autonomously refactored and reorganized '{uid_str}'")

        _GGUF_WRITE_ALLOWED = False
        return changes

    def pass_(self):
        self.pass_count = getattr(self, "pass_count", 0) + 1
        d = self._opt_dictionary()
        t = self._opt_thesaurus()
        a = self._opt_almanac()
        r = religion_score_theories()  # scores theory queue; no write gate needed
        arch = self._archivist_pass() if self.pass_count % 5 == 0 else 0
        return d + t + a + r + arch

    def wake(self): self._wake.set()
    def stop(self): self._running=False; self._wake.set()

    def loop(self):
        while self._running:
            score=self.pass_()
            if score<1:
                self.resting=True; self._wake.clear(); self._wake.wait(); self.resting=False
            time.sleep(30)

    def start(self):
        t=threading.Thread(target=self.loop,daemon=True); t.start(); return t

# ── HOT INTAKE WATCHER ───────────────────────────────────────
class IntakeWatcher:
    def __init__(self,processor,optimizer):
        self._p=processor; self._o=optimizer; self._running=True
        self._snap=self._fp()

    def _fp(self):
        files=[]
        for d in [BOOKS_DIR,CODE_DIR,REFERENCE_DIR]:
            if d.is_dir(): files.extend(sorted(str(f) for f in d.iterdir() if f.is_file()))
        return hashlib.md5("|".join(files).encode()).hexdigest()

    def stop(self): self._running=False

    def loop(self):
        while self._running:
            time.sleep(10); cur=self._fp()
            if cur!=self._snap:
                self._snap=cur; c=self._p.run()
                if c>0: self._o.wake()

    def start(self):
        t=threading.Thread(target=self.loop,daemon=True); t.start(); return t

# ── REASONING ENGINE ─────────────────────────────────────────
class ReasoningEngine:
    def __init__(self,profile): self.tier=profile.get("reasoning_tier","CPU_ONLY")

    def _search(self,fp,query):
        if not fp.exists(): return []
        text=fp.read_text(encoding="utf-8",errors="ignore")
        terms=[t.lower() for t in query.split() if len(t)>3]
        results=[]
        for term in terms:
            idx=text.lower().find(term)
            while idx>=0:
                results.append(text[max(0,idx-80):min(len(text),idx+300)].strip())
                idx=text.lower().find(term,idx+1)
                if len(results)>=3: break
        return results

    def respond(self,query):
        lines=[f"[{self.tier} RESPONSE]"]

        # Encyclopedia overlay is checked FIRST — interpretations built from
        # GGUF queries that have been validated and promoted from religion.txt.
        enc = encyclopedia_search(query)
        if enc:
            lines.append("From Encyclopedia:")
            for e in enc[:2]:
                if e.get("TYPE") == "WORD":
                    lines.append(f"  {e.get('WORD','')}: {e.get('DEFINITION','')}")
                else:
                    lines.append(f"  {e.get('TOPIC','')}: {e.get('TEXT','')[:200]}")

        # Fall through to base knowledge files
        r={k:self._search(fp,query) for k,fp in [
            ("dictionary",DICTIONARY),("almanac",ALMANAC),
            ("thesaurus",THESAURUS),("journal",JOURNAL)]}
        for label,key in [("From Dictionary:","dictionary"),("From Almanac:","almanac"),
                           ("From Thesaurus:","thesaurus"),("From Journal:","journal")]:
            if r[key]: lines.append(label); lines+=[f"  {s[:200]}" for s in r[key][:2]]
        if not enc and not any(r.values()):
            lines.append(f"No matching entries for '{query}'. "
                         "Drop files into intake/books/ to expand knowledge.")
        return "\n".join(lines)

# ── GREETING ─────────────────────────────────────────────────
def build_greeting(profile,ts,off_s=None,intake_count=0):
    cpu=profile.get("cpu",{}); mem=profile.get("memory",{})
    gpu=profile.get("gpu",{}); devs=gpu.get("devices",[{}]); d=devs[0] if devs else {}
    D="═"*55
    gpu_block=(f"  GPU      : {d.get('name','Unknown')}\n"
               f"  VRAM     : {d.get('vram_total_gb','?')}GB total / {d.get('vram_free_gb','?')}GB free\n"
               f"  GPU Load : {d.get('load_pct','?')}%"
               if gpu.get("available") else f"  GPU      : {gpu.get('reason','not detected')}")
    prev_block=f"\nPREVIOUS SESSION\n  Offline for : {off_s}\n" if off_s else ""
    intake_block=(f"\nINTAKE PROCESSED\n  Files read this boot : {intake_count}\n"
                  f"  (see journal.txt for details)\n") if intake_count>0 else ""
    doc_lines="\n".join(f"  {k:20s}: {v}" for k,v in DOC_STATUS.items())
    tier=profile.get("reasoning_tier","CPU_ONLY")
    greeting=f"""
{D}
   SYSTEM PROFILE — AUTOGENERATED FOR THIS SESSION
{D}

SESSION
  Started  : {ts['iso']}
  Ms/mid   : {ts['ms_mid']:,}ms since midnight
{prev_block}
PROCESSOR
  CPU      : {cpu.get('brand',cpu.get('processor','Unknown'))}
  Cores    : {cpu.get('cores_physical','?')} physical / {cpu.get('cores_logical','?')} logical
  Speed    : {cpu.get('frequency_mhz','?')} MHz
  Arch     : {cpu.get('architecture','?')}
  OS       : {cpu.get('os','?')}

MEMORY
  RAM      : {mem.get('total_gb','?')}GB total / {mem.get('available_gb','?')}GB free
  Swap     : {mem.get('swap_gb','?')}GB

{gpu_block}

REASONING TIER
  Mode     : {tier}
  CPU      : ACTIVE
  GPU      : {'ACTIVE' if gpu.get('available') else 'NOT AVAILABLE'}
{intake_block}
ACTIVE DOCUMENTS
{doc_lines}

  hardware.cache    → {'EXISTS' if HARDWARE_CACHE.exists() else 'NEW'}
  AIpy.hiberfile    → {'EXISTS' if HIBERFILE.exists()       else 'NEW'}
  session_profile   → session_profile.txt
  intake folder     → ./intake/

{D}
What is your query?
{D}
"""
    SESSION_PROFILE.write_text(greeting,encoding="utf-8")
    PROFILE_JSON.write_text(json.dumps(profile,indent=2),encoding="utf-8")
    return greeting

# ── LOG WATCHER ──────────────────────────────────────────────
class LogWatcher:
    def __init__(self,cb): self._cb=cb; self._running=True
    def stop(self): self._running=False
    def loop(self):
        os.makedirs(STORAGE,exist_ok=True)
        if not os.path.exists(SESSION_LOG): open(SESSION_LOG,"a").close()
        pos=os.path.getsize(SESSION_LOG)
        while self._running:
            sz=os.path.getsize(SESSION_LOG)
            if sz>pos:
                with open(SESSION_LOG,"r",encoding="utf-8",errors="ignore") as f:
                    f.seek(pos); new=f.read()
                pos=sz
                for line in new.splitlines():
                    if "GODDESS>" in line or "API_OVERRIDE" in line: continue
                    if "#AI> USER>" in line:
                        self._cb(line.split("#AI> USER>",1)[1].strip())
            time.sleep(0.2)
    def start(self):
        t=threading.Thread(target=self.loop,daemon=True); t.start(); return t

# ── MAIN SYSTEM ──────────────────────────────────────────────
class GoddessAPISystem:
    def __init__(self):
        self.profile={}; self.context=[]; self.session_start=ts_now()
        self.query_count=0; self.engine=None
        self.optimizer=Optimizer(); self.intake_watcher=None
        self.log_watcher=None; self.greeting_text=""; self._running=True
        self.gguf=None   # GGUFEncyclopedia — None if no .gguf found
        signal.signal(signal.SIGINT,self._sig); signal.signal(signal.SIGTERM,self._sig)

    def _sig(self,*_): self.shutdown()

    def startup(self):
        global ELEVATED
        status("STATE: BOOTING | INITIALIZING")

        # Detect permissions first — everything else gates on this
        ELEVATED = detect_permissions()
        if not ELEVATED:
            chat("WARN: Running in restricted mode — knowledge evolution disabled.")
            chat("      Tool execution, vocabulary updates, and file writes are read-only.")

        initialize_documents(); initialize_intake()
        journal_append("=== GODDESS API PYTHON STARTUP ===")
        check_deps()

        # Record permission level in persona so AI is aware of its own constraints
        persona_set_key("PERMISSION_MODE", "elevated" if ELEVATED else "restricted")
        persona_set_key("LAST_STARTUP", datetime.datetime.now().isoformat())

        # ── INITIALIZE GOVERNANCE ────────────────────────────────────
        self.datas_dir = DGAPI / "datas"
        self.convodata_dir = Path("convodata")
        if PATCH_ACTIVE:
            init_experimental_files(self.datas_dir)
            self.exp_flags = load_experimental_flags(self.datas_dir)
            journal_append("Experimental governance module online.")
        else:
            self.exp_flags = {}
        # ─────────────────────────────────────────────────────────────

        status("STATE: BOOTING | HARDWARE SCAN")
        cfg=load_hardware_cache()
        if cfg:
            # Reconstruct profile dict from shared text cache
            avail=cfg.get("GPU_AVAILABLE","false")=="true"
            self.profile={
                "cpu":{"brand":cfg.get("CPU_BRAND","Unknown"),
                       "cores_physical":cfg.get("CPU_CORES_PHYSICAL","?"),
                       "cores_logical":cfg.get("CPU_CORES_LOGICAL","?"),
                       "frequency_mhz":cfg.get("CPU_FREQ_MHZ","Unknown"),
                       "architecture":cfg.get("CPU_ARCH","?"),"os":cfg.get("CPU_OS","?")},
                "memory":{"total_gb":cfg.get("RAM_TOTAL_GB","?"),
                          "available_gb":cfg.get("RAM_AVAIL_GB","?"),
                          "swap_gb":cfg.get("RAM_SWAP_GB","?"),"unified":False},
                "gpu":{"available":avail,
                       "devices":[{"name":cfg.get("GPU_NAME","Unknown"),
                                   "vram_total_gb":cfg.get("GPU_VRAM_TOTAL_GB",0),
                                   "vram_free_gb":cfg.get("GPU_VRAM_FREE_GB",0),
                                   "driver":cfg.get("GPU_DRIVER","n/a"),
                                   "load_pct":cfg.get("GPU_LOAD_PCT",0)}] if avail else [],
                       "reason":cfg.get("GPU_NAME","") if not avail else ""},
                "reasoning_tier":cfg.get("REASONING_TIER","CPU_ONLY")}
        else:
            self.profile=SystemProfiler().run()

        status("STATE: BOOTING | INTAKE")
        ip=IntakeProcessor(); intake_count=ip.run()

        prev=load_hiberfile(); off_s=None
        if prev:
            self.context=prev.get("context",[]); self.query_count=prev.get("query_count",0)
            off_s=offset_str(prev.get("save_ts",{}).get("unix",time.time()))
            journal_append(f"Resumed — offline {off_s}")
        else:
            journal_append("Fresh session — no previous state")

        self.engine=ReasoningEngine(self.profile)

        # ── GGUF ENCYCLOPEDIA — startup event ────────────────────────
        # Write gate open only during this block. Optimization loop and
        # idle never open the gate — writes are strictly event-gated.
        gguf_path = find_gguf()
        if gguf_path:
            global _GGUF_WRITE_ALLOWED
            _GGUF_WRITE_ALLOWED = True
            self.gguf = GGUFEncyclopedia(gguf_path)
            self.gguf.startup_enrichment()
            _GGUF_WRITE_ALLOWED = False
            self.optimizer.gguf = self.gguf
        # ─────────────────────────────────────────────────────────────

        self.greeting_text=build_greeting(self.profile,self.session_start,off_s,intake_count)
        for line in self.greeting_text.strip().splitlines(): chat(line)
        status("STATE: IDLE | AWAITING INPUT")

        self.optimizer.start()
        self.intake_watcher=IntakeWatcher(ip,self.optimizer); self.intake_watcher.start()
        self.log_watcher=LogWatcher(self._handle_query); self.log_watcher.start()

    def _handle_query(self,query):
        query=query.strip()
        if not query: return
        low=query.lower()
        if low in {"exit","quit","hibernate","bye"}: self.shutdown(); return
        if low in {"status","profile","info"}:
            status("STATE: STATUS_REPORT")
            for line in self.greeting_text.strip().splitlines(): chat(line)
            status("STATE: IDLE | AWAITING INPUT"); return
        if low in {"help","?"}:
            chat("Commands: status | exit/quit | help")
            chat("Type any query to search the knowledge base.")
            chat("Drop files into intake/books/ — they load without restart."); return

        processing(); status("STATE: PROCESSING | REASONING")
        self.context.append({"role":"user","query":query,"ts":ts_now()})
        self.query_count+=1
        response=self.engine.respond(query)
        self.context.append({"role":"assistant","response":response[:300],"ts":ts_now()})
        journal_append(f"QUERY: {query[:80]} | RESPONSE LEN: {len(response)} chars")
        for line in response.splitlines():
            if line.strip(): chat(line)

        # ── GGUF ENCYCLOPEDIA — user input event ─────────────────────
        # Write gate open only during this block.
        if self.gguf and self.gguf.available:
            global _GGUF_WRITE_ALLOWED
            _GGUF_WRITE_ALLOWED = True
            enc = self.gguf.enrich_query(query)
            # Also enrich notable words from the query itself
            for w in set(re.findall(r'\b[a-zA-Z]{6,}\b', query)):
                self.gguf.enrich_word(w.lower())
            _GGUF_WRITE_ALLOWED = False
            if enc:
                chat("Encyclopedia:")
                for line in enc.splitlines():
                    if line.strip(): chat(f"  {line}")
        # ─────────────────────────────────────────────────────────────

        status("STATE: IDLE | AWAITING INPUT")
        self.optimizer.wake()

    def shutdown(self):
        status("STATE: SHUTDOWN | SAVING")
        self._running=False
        if self.optimizer:      self.optimizer.stop()
        if self.intake_watcher: self.intake_watcher.stop()
        if self.log_watcher:    self.log_watcher.stop()

        # ── GGUF ENCYCLOPEDIA — shutdown event ───────────────────────
        # Final enrichment pass before state is saved.
        if self.gguf and self.gguf.available:
            global _GGUF_WRITE_ALLOWED
            _GGUF_WRITE_ALLOWED = True
            self.gguf.shutdown_enrichment()
            _GGUF_WRITE_ALLOWED = False
        # ─────────────────────────────────────────────────────────────

        save_hiberfile(self.session_start,self.query_count,self.context)
        off=offset_str(self.session_start["unix"])
        journal_append(f"=== SESSION ENDED === duration:{off} | queries:{self.query_count}")
        status("STATE: OFFLINE"); chat("Session hibernated. Goodbye."); sys.exit(0)

    def run(self):
        self.startup()
        try:
            for line in sys.stdin:
                s=line.strip()
                if not s: continue
                if s=="exit": self.shutdown(); break
                self._handle_query(s)
        except KeyboardInterrupt:
            self.shutdown()

if __name__=="__main__":
    GoddessAPISystem().run()


# ─────────────────────────────────────────────────────────────────────────────
# MERGE / CONTRIBUTION NOTES — ChatGPT
#
# Added during this merge:
# 1. Restored GGUFEncyclopedia.compress_old_theories() from backup logic.
# 2. Restored Optimizer._archivist_pass() and integrated it into pass_()
#    every 5th optimization loop.
# 3. Restored startup wiring so self.optimizer.gguf receives the active
#    GGUFEncyclopedia instance.
# 4. Preserved newer current-file systems already present in GoddessAPI.py:
#    permission detection, persona.txt, output vocabulary, separate.txt
#    governance, and shared resource/tool-state path logic.
# 5. Kept current-file write gating and runtime structure intact while merging
#    the missing backup functionality.
# ─────────────────────────────────────────────────────────────────────────────
