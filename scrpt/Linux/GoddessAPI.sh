#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# GoddessAPI.sh — AI Alone Mode
# Pure bash. No Python. No jq.
# Launched by GoddessMatrix when AI button is pressed (no FN).
# Communicates with GoddessMatrix.java via protocol tags on stdout.
# Hibernate state : AIsh.hiberfile  (bash key=value text)
# Hardware cache  : hardware.cache  (shared plain text — also read by GoddessAPI.py)
# ═══════════════════════════════════════════════════════════════

# ── COLORS (stderr only — stdout is reserved for protocol tags) ──
RED='\033[0;31m'; GRN='\033[0;32m'; YLW='\033[1;33m'
BLU='\033[0;34m'; NC='\033[0m'

# ── PROTOCOL TAG OUTPUT ──────────────────────────────────────
# Stdout is the Java protocol pipe. All tags flushed immediately.
status()     { printf '[STATUS] %s\n' "$1"; }
chat()       { printf '[CHAT] %s\n'   "$1"; }
img()        { printf '[IMAGE] %s\n'  "$1"; }
type_cmd()   { printf '[TYPE] %s\n'   "$1"; }
processing() { printf '[PROCESSING]\n';       }

# ── DEPENDENCY CHECK ─────────────────────────────────────────
HAS_INOTIFY=false
HAS_PDFTOTEXT=false

check_dep() {
    local cmd="$1" pkg="$2" required="${3:-false}"
    if command -v "$cmd" &>/dev/null; then
        return 0
    else
        if [ "$required" = true ]; then
            chat "ERROR: Required tool '$cmd' not found (install: $pkg)."
            exit 1
        else
            chat "WARN: $cmd not found ($pkg) — some features limited."
            return 1
        fi
    fi
}

check_dep awk    "gawk/mawk"     true
check_dep sed    "sed"           true
check_dep grep   "grep"          true
check_dep sort   "coreutils"     true
check_dep md5sum "coreutils"     true
check_dep find   "findutils"     true
check_dep inotifywait "inotify-tools" false && HAS_INOTIFY=true
check_dep pdftotext   "poppler-utils" false && HAS_PDFTOTEXT=true

# ── GGUF ENCYCLOPEDIA DETECTION ──────────────────────────────
# Requires a llama.cpp CLI binary and a .gguf file in the working dir.
# Write gate: GGUF_WRITE_ALLOWED=true only during startup, user input,
# and shutdown events — never during the optimization loop or idle.
GGUF_WRITE_ALLOWED=false
GGUF_FILE=""
LLAMA_BIN=""

find_gguf() {
    local f
    for f in "$BASE"/*.gguf; do
        [ -f "$f" ] && GGUF_FILE="$f" && return 0
    done
    return 1
}

find_llama_bin() {
    # Check standard install locations then local directory
    for bin in llama-cli llama llama.cpp; do
        command -v "$bin" &>/dev/null && LLAMA_BIN="$bin" && return 0
    done
    for bin in ./llama-cli ./main ./llama; do
        [ -x "$bin" ] && LLAMA_BIN="$bin" && return 0
    done
    return 1
}

# ── GGUF QUERY ───────────────────────────────────────────────
# NOTE: Each call reloads the model from disk (bash limitation —
# no persistent in-process model state between function calls).
# For high-frequency use, consider llama.cpp server mode instead.
query_gguf() {
    local prompt="$1" max_tokens="${2:-200}"
    [ -n "$GGUF_FILE" ] && [ -n "$LLAMA_BIN" ] || return
    "$LLAMA_BIN" \
        -m "$GGUF_FILE" \
        -p "$prompt" \
        -n "$max_tokens" \
        --log-disable \
        --no-display-prompt \
        -s 42 \
        2>/dev/null | head -c 600
}

# ── GGUF ENCYCLOPEDIA WRITE FUNCTIONS ────────────────────────
# These only execute when GGUF_WRITE_ALLOWED=true.
# GGUF file itself is never modified.
# All derived knowledge flows: GGUF query → religion.txt (proposed)
#   → optimizer scores → encyclopedia.txt (validated overlay).

# ── ENCYCLOPEDIA.TXT ─────────────────────────────────────────
# Overlay: reasoning engine reads this FIRST. Persists across GGUF swaps.
# Written only during write-gated events.

init_encyclopedia() {
    [ -f "$ENCYCLOPEDIA" ] && return
    printf '# Encyclopedia Overlay\n# GGUF-derived interpretations. Persists across GGUF file changes.\n# Written during: startup, user input, shutdown events only.\n' \
        > "$ENCYCLOPEDIA"
}

encyclopedia_search() {
    local query="$1"
    [ -f "$ENCYCLOPEDIA" ] || return
    local term
    for term in $query; do
        [ "${#term}" -gt 3 ] || continue
        grep -i -A6 "$term" "$ENCYCLOPEDIA" 2>/dev/null | head -12
        break
    done
}

encyclopedia_write_entry() {
    [ "$GGUF_WRITE_ALLOWED" = true ] || return
    [ "$ELEVATED" = true ] || return
    init_encyclopedia
    local type="$1"; shift
    local ts; ts=$(ts_now)
    {
        printf '\n[ENTRY]\n'
        printf 'TYPE=%s\n' "$type"
        local arg
        for arg in "$@"; do printf '%s\n' "$arg"; done
        printf 'TIMESTAMP=%s\n' "$ts"
        printf '[/ENTRY]\n'
    } >> "$ENCYCLOPEDIA"
}

# ── RELIGION.TXT ─────────────────────────────────────────────
# Theory queue. New theories written during write-gated events.
# Optimizer freely scores and marks READY_TO_PROMOTE (no gate for scoring).
# Promotion to encyclopedia.txt happens only during write-gated events.
# Score = min(1.0, TIMES_PROPOSED * 0.25). Promotes at score >= 0.70.

init_religion() {
    [ -f "$RELIGION" ] && return
    printf '# Religion — Theoretical Edit Queue\n# New theories written during: startup, user input, shutdown.\n# Scores updated by: optimization loop (no write gate).\n# Promoted to encyclopedia.txt during write-gated events.\n' \
        > "$RELIGION"
}

religion_propose_theory() {
    # Usage: religion_propose_theory UID TYPE KEY=value ...
    [ "$GGUF_WRITE_ALLOWED" = true ] || return
    [ "$ELEVATED" = true ] || return
    init_religion
    local uid="$1" type="$2"; shift 2
    local ts; ts=$(ts_now)

    if grep -q "^ID=${uid}$" "$RELIGION" 2>/dev/null; then
        # Theory exists — increment TIMES_PROPOSED and update LAST_SEEN
        # Use awk to update the correct theory block only
        local tmpf; tmpf=$(mktemp)
        awk -v uid="$uid" -v ts="$ts" '
            /^\[THEORY\]$/ { in_block=1; block="[THEORY]"; next }
            /^\[\/THEORY\]$/ {
                if(in_block) {
                    found=0; n=split(block,lines,"\n")
                    for(i=1;i<=n;i++) if(lines[i]=="ID="uid) found=1
                    for(i=1;i<=n;i++) {
                        if(found && lines[i] ~ /^TIMES_PROPOSED=/) {
                            t=substr(lines[i],16)+1
                            print "TIMES_PROPOSED="t
                        } else if(found && lines[i] ~ /^LAST_SEEN=/) {
                            print "LAST_SEEN="ts
                        } else print lines[i]
                    }
                    print "[/THEORY]"
                }
                in_block=0; block=""; next
            }
            in_block { block=block"\n"$0; next }
            { print }
        ' "$RELIGION" > "$tmpf" && mv "$tmpf" "$RELIGION"
    else
        {
            printf '\n[THEORY]\n'
            printf 'ID=%s\n' "$uid"
            printf 'TYPE=%s\n' "$type"
            local arg
            for arg in "$@"; do printf '%s\n' "$arg"; done
            printf 'TIMES_PROPOSED=1\n'
            printf 'SCORE=0.10\n'
            printf 'FIRST_SEEN=%s\n' "$ts"
            printf 'LAST_SEEN=%s\n' "$ts"
            printf 'STATUS=PENDING\n'
            printf '[/THEORY]\n'
        } >> "$RELIGION"
    fi
}

religion_score_theories() {
    # Score all PENDING theories. Mark qualifying ones READY_TO_PROMOTE.
    # No write gate — only modifies religion.txt (the theory queue), not encyclopedia.
    # Returns count of status changes.
    [ -f "$RELIGION" ] || { echo 0; return; }
    local tmpf changes=0; tmpf=$(mktemp)
    local before after
    before=$(grep -c "^STATUS=READY_TO_PROMOTE$" "$RELIGION" 2>/dev/null || echo 0)

    awk '
    /^\[THEORY\]$/ { in_block=1; block="[THEORY]"; times=1; status="PENDING"; next }
    /^\[\/THEORY\]$/ {
        if(in_block) {
            n=split(block,lines,"\n")
            for(i=1;i<=n;i++) {
                if(lines[i]~/^TIMES_PROPOSED=/) times=substr(lines[i],16)+0
                if(lines[i]~/^STATUS=/)         status=substr(lines[i],8)
            }
            score=times*0.25; if(score>1.0) score=1.0
            new_status=status
            if(status=="PENDING" && score>=0.70) new_status="READY_TO_PROMOTE"
            for(i=1;i<=n;i++) {
                if(lines[i]~/^SCORE=/)  printf "SCORE=%.2f\n",score
                else if(lines[i]~/^STATUS=/) print "STATUS="new_status
                else print lines[i]
            }
            print "[/THEORY]"
        }
        in_block=0; block=""; next
    }
    in_block { block=block"\n"$0; next }
    { print }
    ' "$RELIGION" > "$tmpf" && mv "$tmpf" "$RELIGION"

    after=$(grep -c "^STATUS=READY_TO_PROMOTE$" "$RELIGION" 2>/dev/null || echo 0)
    changes=$((after - before))
    [ "$changes" -lt 0 ] && changes=0
    echo "$changes"
}

religion_promote_ready() {
    # Promote READY_TO_PROMOTE theories → encyclopedia.txt, mark PROMOTED.
    # Only runs when GGUF_WRITE_ALLOWED=true.
    [ "$GGUF_WRITE_ALLOWED" = true ] || return
    [ -f "$RELIGION" ] || return
    local max="${1:-5}" count=0 tmpf; tmpf=$(mktemp)
    cp "$RELIGION" "$tmpf"

    # Extract each READY_TO_PROMOTE block and promote it
    awk '
    /^\[THEORY\]$/ { in_block=1; block=""; next }
    /^\[\/THEORY\]$/ {
        if(in_block) {
            n=split(block,lines,"\n")
            status=""
            for(i=1;i<=n;i++) if(lines[i]~/^STATUS=/) status=substr(lines[i],8)
            if(status=="READY_TO_PROMOTE") {
                # Print the block content as pipe-delimited promotion record
                print "<<<PROMOTE>>>"
                for(i=1;i<=n;i++) print lines[i]
                print "<<<END>>>"
            }
        }
        in_block=0; block=""; next
    }
    in_block { block=block (length(block)>0?"\n":"") $0; next }
    ' "$RELIGION" | {
        local in_promote=false promote_block=""
        while IFS= read -r line; do
            if [ "$line" = "<<<PROMOTE>>>" ]; then
                in_promote=true; promote_block=""; continue
            fi
            if [ "$line" = "<<<END>>>" ]; then
                in_promote=false
                [ "$count" -ge "$max" ] && continue
                # Parse block fields
                local ptype="" word="" topic="" pdef="" ptext="" psyn="" src="" uid=""
                while IFS= read -r fline; do
                    case "$fline" in
                        ID=*)                uid="${fline#ID=}" ;;
                        TYPE=*)              ptype="${fline#TYPE=}" ;;
                        WORD=*)              word="${fline#WORD=}" ;;
                        TOPIC=*)             topic="${fline#TOPIC=}" ;;
                        PROPOSED_DEFINITION=*) pdef="${fline#PROPOSED_DEFINITION=}" ;;
                        PROPOSED_TEXT=*)     ptext="${fline#PROPOSED_TEXT=}" ;;
                        PROPOSED_SYNONYMS=*) psyn="${fline#PROPOSED_SYNONYMS=}" ;;
                        SOURCE=*)            src="${fline#SOURCE=}" ;;
                    esac
                done <<< "$promote_block"
                # Write to encyclopedia
                if [ "$ptype" = "WORD" ] && [ -n "$word" ]; then
                    encyclopedia_write_entry "WORD" \
                        "WORD=$word" "DEFINITION=$pdef" \
                        "SYNONYMS=$psyn" "SOURCE=$src"
                elif [ -n "$topic" ]; then
                    encyclopedia_write_entry "TOPIC" \
                        "TOPIC=$topic" "TEXT=$ptext" "SOURCE=$src"
                fi
                # Mark promoted in tmpf
                [ -n "$uid" ] && sed -i \
                    "/^ID=${uid}$/,/^\[\/THEORY\]$/ s/^STATUS=READY_TO_PROMOTE$/STATUS=PROMOTED/" \
                    "$tmpf" 2>/dev/null || true
                count=$((count+1))
                continue
            fi
            $in_promote && promote_block="${promote_block}${line}
"
        done
        cp "$tmpf" "$RELIGION"
        rm -f "$tmpf"
        [ "$count" -gt 0 ] && \
            journal_append "GGUF PROMOTED: $count theories → encyclopedia.txt"
    }
}

gguf_enrich_word() {
    local word="$1"
    [ "$GGUF_WRITE_ALLOWED" = true ] || return
    [ -n "$GGUF_FILE" ] || return

    local uid; uid="word_$(printf '%s' "$word" | tr '[:upper:] ' '[:lower:]_')"
    local defn syns
    defn=$(query_gguf \
        "Write a single-sentence dictionary definition for '$word'.\nDefinition:" 80)
    syns=$(query_gguf \
        "List 3-5 synonyms for '$word', comma-separated:\nSynonyms:" 50)

    [ -n "$defn" ] && religion_propose_theory "$uid" "WORD" \
        "WORD=$word" \
        "PROPOSED_DEFINITION=$defn" \
        "PROPOSED_SYNONYMS=${syns:-—}" \
        "SOURCE=$(basename "$GGUF_FILE")"

    religion_promote_ready
}

gguf_enrich_query() {
    local query="$1"
    [ "$GGUF_WRITE_ALLOWED" = true ] || return
    [ -n "$GGUF_FILE" ] || return

    local response
    response=$(query_gguf \
        "Provide a brief encyclopedia-style explanation of: $query\nKeep it to 2-3 sentences.\nEncyclopedia entry:" \
        200)
    [ -z "$response" ] && return

    local uid; uid="topic_$(printf '%s' "${query:0:30}" | tr '[:upper:] ' '[:lower:]_')"
    religion_propose_theory "$uid" "TOPIC" \
        "TOPIC=${query:0:60}" \
        "PROPOSED_TEXT=${response:0:200}" \
        "SOURCE=$(basename "$GGUF_FILE")"

    religion_promote_ready
    journal_append "GGUF QUERY: ${query:0:60} | RESULT LEN: ${#response}"
    printf '%s' "$response"
}

gguf_startup_enrichment() {
    [ "$GGUF_WRITE_ALLOWED" = true ] || return
    [ -n "$GGUF_FILE" ] || return

    # Promote any theories that matured while offline
    local promoted=0
    religion_promote_ready 10
    [ -f "$DICTIONARY" ] || return

    local words count=0
    words=$(grep -oE '^[a-zA-Z]+' "$DICTIONARY" 2>/dev/null | head -20)
    while IFS= read -r word; do
        [ -z "$word" ] && continue
        gguf_enrich_word "$word"
        count=$((count+1))
    done <<< "$words"
    [ "$count" -gt 0 ] && chat "Encyclopedia: proposed $count word theories at startup."
}

gguf_shutdown_enrichment() {
    [ "$GGUF_WRITE_ALLOWED" = true ] || return
    [ -n "$GGUF_FILE" ] || return
    [ -f "$DICTIONARY" ] || return

    local sparse count=0
    sparse=$(grep -B1 "  Term in " "$DICTIONARY" 2>/dev/null \
        | grep -oE '^[a-zA-Z]+' | head -10)
    while IFS= read -r word; do
        [ -z "$word" ] && continue
        gguf_enrich_word "$word"
        count=$((count+1))
    done <<< "$sparse"

    religion_promote_ready 10
    [ "$count" -gt 0 ] && journal_append "GGUF SHUTDOWN: proposed $count theories"
}

# ── FILE PATHS ───────────────────────────────────────────────
BASE="."
STORAGE="convodata"
HARDWARE_CACHE="$BASE/hardware.cache"
HIBERFILE="$BASE/AIsh.hiberfile"
SESSION_PROFILE="$BASE/session_profile.txt"
JOURNAL="$BASE/journal.txt"
DICTIONARY="$BASE/dictionary.txt"
THESAURUS="$BASE/thesaurus.txt"
ALMANAC="$BASE/almanac.txt"
INTAKE_DIR="$BASE/intake"
BOOKS_DIR="$INTAKE_DIR/books"
CODE_DIR="$INTAKE_DIR/code"
REFERENCE_DIR="$INTAKE_DIR/reference"
PROCESSED_DIR="$INTAKE_DIR/processed"
INTAKE_LOG="$INTAKE_DIR/intake.log"
SESSION_LOG="$STORAGE/convodata.txt"
ENCYCLOPEDIA="$BASE/encyclopedia.txt"
RELIGION="$BASE/religion.txt"
PERSONA="$BASE/persona.txt"                    # AI self-model — no schema, AI decides content
OUTPUT_VOCAB="$BASE/output_vocabulary.txt"     # shared unless separate.txt says otherwise
OUTPUT_VOCAB_SH="$BASE/output_vocabulary_sh.txt"
OUTPUT_VOCAB_PY="$BASE/output_vocabulary_py.txt"
SEPARATE_TXT="$STORAGE/separate.txt"           # governs sharing of resources and vocab
RESOURCES_DIR="$BASE/resources"
TOOL_STATE="$BASE/tool_state.txt"

# ── PERMISSION DETECTION ─────────────────────────────────────
# Elevated: full knowledge evolution, tool execution with persistence.
# Restricted: read-only LLM — queries work, no writes to knowledge files.
ELEVATED=false

detect_permissions() {
    # Root is always elevated
    [ "$(id -u)" -eq 0 ] && ELEVATED=true && return
    # Test write access to working directory
    local probe="$BASE/.goddess_perm_probe_$$"
    if touch "$probe" 2>/dev/null; then
        rm -f "$probe"
        ELEVATED=true
    else
        ELEVATED=false
    fi
}

# ── SEPARATE.TXT READER ──────────────────────────────────────
# "yes"  → tool_state.txt is per-runtime
# "no"   → output vocabulary uses two separate files
# empty  → shared file, each runtime ignores other's prefixed lines
read_separate() {
    [ -f "$SEPARATE_TXT" ] || { printf ''; return; }
    tr '[:upper:]' '[:lower:]' < "$SEPARATE_TXT" | tr -d '[:space:]'
}

tool_state_is_shared() {
    [ "$(read_separate)" != "yes" ]
}

vocab_file() {
    # Returns the vocab file path this runtime should use
    [ "$(read_separate)" = "no" ] && printf '%s' "$OUTPUT_VOCAB_SH" || printf '%s' "$OUTPUT_VOCAB"
}

# ── PERSONA.TXT ───────────────────────────────────────────────
# Shared. No schema. AI writes what it finds useful.

init_persona() {
    [ -f "$PERSONA" ] && return
    printf '# persona.txt\n# This file belongs to the AI. It may write whatever it finds useful here.\n' \
        > "$PERSONA"
}

persona_read() {
    [ -f "$PERSONA" ] && cat "$PERSONA" || printf ''
}

persona_set_key() {
    # Usage: persona_set_key KEY VALUE
    [ "$ELEVATED" = true ] || return
    init_persona
    local key="$1" value="$2"
    if grep -q "^${key}=" "$PERSONA" 2>/dev/null; then
        sed -i "s|^${key}=.*|${key}=${value}|" "$PERSONA"
    else
        printf '%s=%s\n' "$key" "$value" >> "$PERSONA"
    fi
}

persona_get_key() {
    local key="$1" default="${2:-}"
    local val
    val=$(grep -m1 "^${key}=" "$PERSONA" 2>/dev/null | cut -d= -f2-)
    printf '%s' "${val:-$default}"
}

# ── OUTPUT VOCABULARY ─────────────────────────────────────────
# Determines how this runtime formats and presents output.
# sh:-prefixed lines are sh-only. py:-prefixed lines are skipped.
# Untagged lines are shared.
# Evolution: AI may update entries at reboot, prompt, resource scan.
# Only when ELEVATED.

_VOCAB_BASELINE_SH='# Output Vocabulary — Bash/AI-alone mode
# Untagged lines are shared with the Python runtime.
# Lines prefixed sh: are used only by this runtime.
# Modify freely — this is your vocabulary to evolve.

TONE=analytical
GREETING_STYLE=formal
sh:COLOR_STATUS=\033[0;34m
sh:COLOR_CHAT=\033[0;32m
sh:COLOR_WARN=\033[1;33m
sh:COLOR_ERROR=\033[0;31m
sh:COLOR_RESET=\033[0m
sh:USE_UNICODE_BORDERS=true
BORDER_CHAR=═
DIVIDER_WIDTH=55
sh:RESPONSE_MAX_LINES=60
'

init_output_vocab() {
    local vf; vf=$(vocab_file)
    [ -f "$vf" ] && return
    printf '%s' "$_VOCAB_BASELINE_SH" > "$vf"
}

vocab_get() {
    # Usage: vocab_get KEY [default]
    local key="$1" default="${2:-}" vf
    vf=$(vocab_file)
    [ -f "$vf" ] || { printf '%s' "$default"; return; }
    # Read sh:-prefixed and untagged lines; skip py: lines
    local val
    val=$(awk -F= -v k="$key" '
        /^[[:space:]]*#/ { next }
        /^py:/ { next }
        /^sh:/ { sub(/^sh:/,"") }
        $1==k { print substr($0, length($1)+2); exit }
    ' "$vf")
    printf '%s' "${val:-$default}"
}

vocab_set() {
    # Usage: vocab_set KEY VALUE [prefix]
    # prefix: '' shared, 'sh:' sh-only, 'py:' py-only
    [ "$ELEVATED" = true ] || return
    init_output_vocab
    local key="$1" value="$2" prefix="${3:-}" vf full_key
    vf=$(vocab_file)
    full_key="${prefix}${key}"
    if grep -q "^${full_key}=" "$vf" 2>/dev/null; then
        sed -i "s|^${full_key}=.*|${full_key}=${value}|" "$vf"
    else
        printf '%s=%s\n' "$full_key" "$value" >> "$vf"
    fi
    journal_append "VOCAB SET: ${full_key}=${value}"
}

vocab_append_new() {
    # Add a new vocabulary concept the AI invented.
    [ "$ELEVATED" = true ] || return
    init_output_vocab
    local key="$1" value="$2" prefix="${3:-}" vf full_key
    vf=$(vocab_file)
    full_key="${prefix}${key}"
    grep -q "^${full_key}=" "$vf" 2>/dev/null && return  # already exists
    printf '%s=%s\n' "$full_key" "$value" >> "$vf"
    journal_append "VOCAB NEW: ${full_key}=${value}"
}
OPTIM_WAKE_FILE="/tmp/goddess_sh_optim_wake_$$"

mkdir -p "$STORAGE" "$BOOKS_DIR" "$CODE_DIR" "$REFERENCE_DIR" "$PROCESSED_DIR"

# ── TIMESTAMP UTILITIES ──────────────────────────────────────
ts_now()    { date '+%Y-%m-%dT%H:%M:%S'; }
ts_unix()   { date '+%s'; }
ts_date()   { date '+%Y-%m-%d'; }
ts_ms_mid() {
    local h m s
    h=$(date '+%H'); m=$(date '+%M'); s=$(date '+%S')
    printf '%d' $(( (10#$h*3600 + 10#$m*60 + 10#$s) * 1000 ))
}

journal_append() {
    printf '\n[%s | %sms] %s' "$(ts_now)" "$(ts_ms_mid)" "$1" >> "$JOURNAL"
}

# ── DOCUMENT INITIALIZATION ──────────────────────────────────
DOC_DICT="LOADED"; DOC_THES="LOADED"; DOC_ALMA="LOADED"; DOC_JOUR="LOADED"

init_doc() {
    local path="$1" header="$2" var="$3"
    if [ ! -f "$path" ]; then
        printf '%s\n' "$header" > "$path"
        eval "$var=CREATED"
    fi
}

init_documents() {
    init_doc "$DICTIONARY" "# Dictionary — autogenerated" DOC_DICT
    init_doc "$THESAURUS"  "# Thesaurus — autogenerated"  DOC_THES
    init_doc "$ALMANAC"    "# Almanac — autogenerated"    DOC_ALMA
    init_doc "$JOURNAL"    "# Journal — session log"      DOC_JOUR
    init_encyclopedia
    init_religion
    init_persona
    init_output_vocab
}

init_intake() {
    [ -f "$INTAKE_LOG" ] || printf '# Intake Log\n' > "$INTAKE_LOG"
    local readme="$INTAKE_DIR/README.txt"
    [ -f "$readme" ] && return
    cat > "$readme" << 'RDEOF'
INTAKE FOLDER
=============
Drop files here. They are processed automatically at boot
and picked up in real-time during a running session.

/books/     -> txt, pdf books
/code/      -> source code files
/reference/ -> reference texts

Files move to /processed/ after reading.
RDEOF
}

# ── HARDWARE CACHE (plain text key=value, shared format) ─────
cache_get() {
    # Usage: cache_get KEY  →  prints value
    grep -m1 "^$1=" "$HARDWARE_CACHE" 2>/dev/null | cut -d= -f2-
}

generate_fingerprint() {
    local cpu arch cores ram
    cpu=$(grep -m1 "model name" /proc/cpuinfo 2>/dev/null \
        | cut -d: -f2 | xargs || echo "unknown")
    arch=$(uname -m)
    cores=$(nproc 2>/dev/null || echo 0)
    ram=$(awk '/^MemTotal/{print $2}' /proc/meminfo 2>/dev/null || echo 0)
    printf '%s%s%s%s' "$cpu" "$arch" "$cores" "$ram" | md5sum | cut -d' ' -f1
}

# Populates CACHE_* variables from hardware.cache
load_hardware_cache() {
    [ -f "$HARDWARE_CACHE" ] || return 1
    local cached_fp current_fp
    cached_fp=$(cache_get FINGERPRINT)
    current_fp=$(generate_fingerprint)
    [ "$cached_fp" = "$current_fp" ] || return 1
    while IFS='=' read -r key val; do
        [[ "$key" =~ ^# || -z "$key" ]] && continue
        declare -g "CACHE_${key}=${val}"
    done < "$HARDWARE_CACHE"
    return 0
}

scan_and_save_hardware() {
    # CPU from /proc/cpuinfo
    CACHE_CPU_BRAND=$(grep -m1 "model name" /proc/cpuinfo 2>/dev/null \
        | cut -d: -f2 | xargs || echo "Unknown")
    CACHE_CPU_CORES_PHYSICAL=$(grep -c "^processor" /proc/cpuinfo 2>/dev/null || echo "?")
    CACHE_CPU_CORES_LOGICAL=$(nproc 2>/dev/null || echo "?")
    CACHE_CPU_FREQ_MHZ=$(grep -m1 "cpu MHz" /proc/cpuinfo 2>/dev/null \
        | cut -d: -f2 | xargs || echo "Unknown")
    CACHE_CPU_ARCH=$(uname -m)
    CACHE_CPU_OS=$(uname -s)

    # Memory from /proc/meminfo
    local mt ma st
    mt=$(awk '/^MemTotal:/{print $2}'     /proc/meminfo 2>/dev/null || echo 0)
    ma=$(awk '/^MemAvailable:/{print $2}' /proc/meminfo 2>/dev/null || echo 0)
    st=$(awk '/^SwapTotal:/{print $2}'    /proc/meminfo 2>/dev/null || echo 0)
    CACHE_RAM_TOTAL_GB=$(awk "BEGIN{printf \"%.2f\",$mt/1048576}")
    CACHE_RAM_AVAIL_GB=$(awk "BEGIN{printf \"%.2f\",$ma/1048576}")
    CACHE_RAM_SWAP_GB=$(awk  "BEGIN{printf \"%.2f\",$st/1048576}")

    # GPU via nvidia-smi — returncode is the signal, no exception wrapping needed
    local gpu_csv
    gpu_csv=$(nvidia-smi \
        --query-gpu=name,memory.total,memory.free,driver_version,utilization.gpu \
        --format=csv,noheader,nounits 2>/dev/null)
    if [ $? -eq 0 ] && [ -n "$gpu_csv" ]; then
        CACHE_GPU_AVAILABLE=true
        CACHE_GPU_NAME=$(   printf '%s' "$gpu_csv" | cut -d, -f1 | xargs)
        local vt vf
        vt=$(printf '%s' "$gpu_csv" | cut -d, -f2 | xargs)
        vf=$(printf '%s' "$gpu_csv" | cut -d, -f3 | xargs)
        CACHE_GPU_VRAM_TOTAL_GB=$(awk "BEGIN{printf \"%.2f\",$vt/1024}")
        CACHE_GPU_VRAM_FREE_GB=$( awk "BEGIN{printf \"%.2f\",$vf/1024}")
        CACHE_GPU_DRIVER=$(  printf '%s' "$gpu_csv" | cut -d, -f4 | xargs)
        CACHE_GPU_LOAD_PCT=$(printf '%s' "$gpu_csv" | cut -d, -f5 | xargs)
        CACHE_REASONING_TIER="DISCRETE_DUAL_PROCESSOR"
    else
        CACHE_GPU_AVAILABLE=false
        CACHE_GPU_NAME="nvidia-smi absent or no devices"
        CACHE_GPU_VRAM_TOTAL_GB=0; CACHE_GPU_VRAM_FREE_GB=0
        CACHE_GPU_DRIVER="n/a"; CACHE_GPU_LOAD_PCT=0
        CACHE_REASONING_TIER="CPU_ONLY"
    fi

    # Write shared plain-text cache
    {
        printf '# GoddessMatrix Hardware Cache\n'
        printf '# Shared format: readable by GoddessAPI.sh and GoddessAPI.py\n'
        printf 'FINGERPRINT=%s\n'        "$(generate_fingerprint)"
        printf 'CACHED_AT=%s\n'          "$(ts_now)"
        printf 'CPU_BRAND=%s\n'          "$CACHE_CPU_BRAND"
        printf 'CPU_CORES_PHYSICAL=%s\n' "$CACHE_CPU_CORES_PHYSICAL"
        printf 'CPU_CORES_LOGICAL=%s\n'  "$CACHE_CPU_CORES_LOGICAL"
        printf 'CPU_FREQ_MHZ=%s\n'       "$CACHE_CPU_FREQ_MHZ"
        printf 'CPU_ARCH=%s\n'           "$CACHE_CPU_ARCH"
        printf 'CPU_OS=%s\n'             "$CACHE_CPU_OS"
        printf 'RAM_TOTAL_GB=%s\n'       "$CACHE_RAM_TOTAL_GB"
        printf 'RAM_AVAIL_GB=%s\n'       "$CACHE_RAM_AVAIL_GB"
        printf 'RAM_SWAP_GB=%s\n'        "$CACHE_RAM_SWAP_GB"
        printf 'GPU_AVAILABLE=%s\n'      "$CACHE_GPU_AVAILABLE"
        printf 'GPU_NAME=%s\n'           "$CACHE_GPU_NAME"
        printf 'GPU_VRAM_TOTAL_GB=%s\n'  "$CACHE_GPU_VRAM_TOTAL_GB"
        printf 'GPU_VRAM_FREE_GB=%s\n'   "$CACHE_GPU_VRAM_FREE_GB"
        printf 'GPU_DRIVER=%s\n'         "$CACHE_GPU_DRIVER"
        printf 'GPU_LOAD_PCT=%s\n'       "$CACHE_GPU_LOAD_PCT"
        printf 'REASONING_TIER=%s\n'     "$CACHE_REASONING_TIER"
    } > "$HARDWARE_CACHE"
}

# ── HIBERNATE STATE (AIsh.hiberfile, bash key=value) ─────────
QUERY_COUNT=0
CONTEXT_COUNT=0
SESSION_START_UNIX=0
SAVE_UNIX=0

save_hiberfile() {
    local ts unix ms
    ts=$(ts_now); unix=$(ts_unix); ms=$(ts_ms_mid)
    {
        printf '# AIsh Hibernate State — GoddessAPI.sh\n'
        printf 'VERSION=1.0\n'
        printf 'SESSION_START_UNIX=%s\n' "$SESSION_START_UNIX"
        printf 'SAVE_TS=%s\n'            "$ts"
        printf 'SAVE_UNIX=%s\n'          "$unix"
        printf 'SAVE_MS_MID=%s\n'        "$ms"
        printf 'QUERY_COUNT=%s\n'        "$QUERY_COUNT"
        printf 'CONTEXT_COUNT=%s\n'      "$CONTEXT_COUNT"
        local i=0
        while [ $i -lt "$CONTEXT_COUNT" ]; do
            local rv dv tv
            rv="CTX_${i}_ROLE"; dv="CTX_${i}_DATA"; tv="CTX_${i}_TS"
            printf 'CTX_%d_ROLE=%s\n' "$i" "${!rv}"
            printf 'CTX_%d_DATA=%s\n' "$i" "${!dv}"
            printf 'CTX_%d_TS=%s\n'   "$i" "${!tv}"
            i=$((i+1))
        done
    } > "$HIBERFILE"
    journal_append "STATE SAVED | queries:$QUERY_COUNT | ms-mid:$ms"
}

load_hiberfile() {
    [ -f "$HIBERFILE" ] || return 1
    while IFS='=' read -r key val; do
        [[ "$key" =~ ^# || -z "$key" ]] && continue
        declare -g "$key=$val"
    done < "$HIBERFILE"
    return 0
}

ctx_append() {
    local role="$1" data="$2"
    declare -g "CTX_${CONTEXT_COUNT}_ROLE=$role"
    declare -g "CTX_${CONTEXT_COUNT}_DATA=${data:0:300}"
    declare -g "CTX_${CONTEXT_COUNT}_TS=$(ts_unix)"
    CONTEXT_COUNT=$((CONTEXT_COUNT+1))
}

offline_duration() {
    local su="${SAVE_UNIX:-0}" now d h m s elapsed
    now=$(ts_unix)
    elapsed=$((now - su))
    d=$((elapsed/86400))
    h=$(( (elapsed%86400)/3600 ))
    m=$(( (elapsed%3600)/60 ))
    s=$((elapsed%60))
    printf '%dd %dh %dm %ds' "$d" "$h" "$m" "$s"
}

# ── TEXT EXTRACTION (bash, no Python) ────────────────────────
extract_file() {
    local fp="$1" ext="${1##*.}"
    case "${ext,,}" in
        txt|sh|py|js|c|cpp|cs|rb|go|ts|java|md) cat "$fp" ;;
        pdf)
            $HAS_PDFTOTEXT \
                && pdftotext "$fp" - 2>/dev/null \
                || printf '' ;;
        *) printf '' ;;
    esac
}

ext_supported() {
    local ext="${1##*.}"
    case "${ext,,}" in
        txt|sh|py|js|c|cpp|cs|rb|go|ts|java|md|pdf) return 0 ;;
        *) return 1 ;;
    esac
}

# ── TEXT ANALYSIS (awk/grep/sort) ────────────────────────────
notable_words() {
    printf '%s' "$1" \
        | grep -oE '[a-zA-Z]{6,}' \
        | tr '[:upper:]' '[:lower:]' \
        | sort | uniq -c | sort -rn \
        | awk '$1 >= 2 {print $2}' \
        | head -50
}

proper_nouns() {
    printf '%s' "$1" \
        | grep -oE '\b[A-Z][a-z]{2,}( [A-Z][a-z]+)?\b' \
        | sort | uniq -c | sort -rn \
        | awk '$1 >= 3 {$1=""; print substr($0,2)}' \
        | head -30
}

factual_sentences() {
    printf '%s' "$1" \
        | grep -E '\b(is a|was a|are a|known as|called|defined as|refers to|means)\b' \
        | awk 'NF>=5 && NF<=30' \
        | head -15
}

# ── DOCUMENT WRITERS ─────────────────────────────────────────
word_in_dict() { grep -qiE "^${1} \(n" "$DICTIONARY" 2>/dev/null; }
word_in_thes()  {
    local u; u=$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')
    grep -q "^${u}$" "$THESAURUS" 2>/dev/null
}

write_dict_entry() {
    local word="$1" def="$2" src="$3" dt="$4"
    [ "$ELEVATED" = true ] || return 1
    word_in_dict "$word" && return 1
    local L; L=$(printf '%s' "${word:0:1}" | tr '[:lower:]' '[:upper:]')
    grep -q "^${L}$" "$DICTIONARY" 2>/dev/null || {
        printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n%s\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n' \
            "$L" >> "$DICTIONARY"
    }
    printf '\n%s (n.)\n  %s\n  Source : %s\n  Added  : %s\n' \
        "$word" "$def" "$src" "$dt" >> "$DICTIONARY"
    return 0
}

write_thes_entry() {
    local word="$1" ctx="$2" src="$3"
    [ "$ELEVATED" = true ] || return 1
    word_in_thes "$word" && return 1
    local U; U=$(printf '%s' "$word" | tr '[:lower:]' '[:upper:]')
    printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n%s\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n  Similar  : —\n  Opposite : —\n  Context  : "%s..."\n  Source   : %s\n' \
        "$U" "${ctx:0:70}" "$src" >> "$THESAURUS"
    return 0
}

write_almanac_entry() {
    local cat="$1" name="$2" desc="$3" src="$4"
    [ "$ELEVATED" = true ] || return 1
    local CU; CU=$(printf '%s' "$cat" | tr '[:lower:]' '[:upper:]')
    grep -q "^${CU}$" "$ALMANAC" 2>/dev/null || {
        printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n%s\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n' \
            "$CU" >> "$ALMANAC"
    }
    printf '\n  %s\n    %s\n    Source: %s\n' \
        "${name:0:60}" "${desc:0:120}" "$src" >> "$ALMANAC"
}

# ── INTAKE PROCESSOR ─────────────────────────────────────────
process_intake_file() {
    local fp="$1"
    local fname src dt text new_words=0 new_thes=0 al_count=0 wcount

    fname=$(basename "$fp")
    src="${fname%.*}"
    dt=$(ts_date)

    status "STATE: READING | $fname"

    text=$(extract_file "$fp")
    if [ -z "$text" ]; then
        chat "Cannot extract text from $fname — skipping"
        return 1
    fi

    wcount=$(printf '%s' "$text" | wc -w | xargs)

    while IFS= read -r word; do
        [ -z "$word" ] && continue
        local ctx
        ctx=$(printf '%s' "$text" | grep -o ".\{0,30\}${word}.\{0,30\}" | head -1)
        write_dict_entry "$word" "Term encountered in $src." "$src" "$dt" \
            && new_words=$((new_words+1))
        write_thes_entry "$word" "$ctx" "$src" \
            && new_thes=$((new_thes+1))
    done <<< "$(notable_words "$text")"

    while IFS= read -r name; do
        [ -z "$name" ] && continue
        write_almanac_entry "PEOPLE & PLACES" "$name" \
            "Referenced in $src." "$src"
        al_count=$((al_count+1))
    done <<< "$(proper_nouns "$text")"

    while IFS= read -r fact; do
        [ -z "$fact" ] && continue
        write_almanac_entry "NOTABLE FACTS" "${fact:0:50}..." "$fact" "$src"
        al_count=$((al_count+1))
    done <<< "$(factual_sentences "$text")"

    {
        printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
        printf 'READING LOG — %s\n' "$src"
        printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
        printf '[%s | %sms]\n\n' "$(ts_now)" "$(ts_ms_mid)"
        printf '  File     : %s\n' "$fname"
        printf '  Words    : ~%s\n' "$wcount"
        printf '  Dict     : +%s entries\n' "$new_words"
        printf '  Thesaurus: +%s entries\n' "$new_thes"
        printf '  Almanac  : +%s entries\n' "$al_count"
    } >> "$JOURNAL"

    printf '[%s] READ: %s | words:%s | dict:+%s | almanac:+%s\n' \
        "$(ts_now)" "$fname" "$wcount" "$new_words" "$al_count" >> "$INTAKE_LOG"

    local dest="$PROCESSED_DIR/$fname"
    [ -f "$dest" ] && dest="$PROCESSED_DIR/${src}_$(ts_unix).${fp##*.}"
    mv "$fp" "$dest"

    chat "Read $fname — dict:+$new_words almanac:+$al_count"
    return 0
}

process_all_intake() {
    local count=0 fp
    for dir in "$BOOKS_DIR" "$CODE_DIR" "$REFERENCE_DIR"; do
        [ -d "$dir" ] || continue
        for fp in "$dir"/*; do
            [ -f "$fp" ] || continue
            ext_supported "$fp" || continue
            process_intake_file "$fp" && count=$((count+1))
        done
    done
    printf '%d' "$count"
}

# ── SELF-OPTIMIZATION ENGINE ─────────────────────────────────
# Runs passes on knowledge files: sorts entries, removes duplicates.
# Returns the number of changes made in the pass.
# When changes drop below 1, enters rest state until woken.

_sort_blocks() {
    # Sort named blocks in a file. Each block starts with a line matching $block_start.
    # block_start: regex for the header line of each block
    local file="$1"
    local tmpfile; tmpfile=$(mktemp)
    cp "$file" "$tmpfile"
    # Count headwords before and after sort to detect changes
    local before after
    before=$(grep -cE '^\S+ \(n\.\)' "$file" 2>/dev/null || echo 0)
    sort -k1,1 "$file" -o "$file" 2>/dev/null || true
    after=$(grep -cE '^\S+ \(n\.\)' "$file" 2>/dev/null || echo 0)
    # Restore from backup since naive sort breaks structure
    cp "$tmpfile" "$file"
    rm -f "$tmpfile"
    echo $((before - after))
}

optimize_dictionary() {
    local changes=0
    # Detect and count duplicate headwords
    local dupes unique
    dupes=$(grep -cE '^\S+ \(n\.\)' "$DICTIONARY" 2>/dev/null || echo 0)
    unique=$(grep -E '^\S+ \(n\.\)' "$DICTIONARY" \
        | awk '{print $1}' | sort -u | wc -l | xargs)
    local removed=$(( dupes - unique ))
    [ "$removed" -gt 0 ] && changes=$((changes + removed))

    # Check alphabetical order within each section
    local prev_word="" word
    while IFS= read -r line; do
        word=$(printf '%s' "$line" | grep -oE '^\S+' | head -1)
        [ -z "$word" ] && continue
        if [[ "$word" > "$prev_word" ]] && [ -n "$prev_word" ]; then
            : # in order
        elif [ -n "$prev_word" ] && [[ "$word" < "$prev_word" ]]; then
            changes=$((changes+1))
        fi
        prev_word="$word"
    done < <(grep -E '^\S+ \(n\.\)' "$DICTIONARY" 2>/dev/null)

    echo "$changes"
}

optimize_thesaurus() {
    local changes=0
    local dupes unique
    dupes=$(grep -cE '^[A-Z_]+$' "$THESAURUS" 2>/dev/null || echo 0)
    unique=$(grep -E '^[A-Z_]+$' "$THESAURUS" | sort -u | wc -l | xargs)
    changes=$(( dupes - unique ))
    [ "$changes" -lt 0 ] && changes=0
    echo "$changes"
}

optimize_almanac() {
    local changes=0
    local dupes unique
    dupes=$(grep -cE '^  [A-Z]' "$ALMANAC" 2>/dev/null || echo 0)
    unique=$(grep -E '^  [A-Z]' "$ALMANAC" | sort -u | wc -l | xargs)
    changes=$(( dupes - unique ))
    [ "$changes" -lt 0 ] && changes=0
    echo "$changes"
}

run_optimization_pass() {
    local d t a r total
    d=$(optimize_dictionary)
    t=$(optimize_thesaurus)
    a=$(optimize_almanac)
    r=$(religion_score_theories)   # scores theory queue; no write gate needed
    total=$((d + t + a + r))
    echo "$total"
}

wake_optimizer() { touch "$OPTIM_WAKE_FILE" 2>/dev/null || true; }

optimization_loop() {
    while true; do
        local score
        score=$(run_optimization_pass)
        if [ "$score" -lt 1 ]; then
            # Rest until woken by user input or new intake file
            while [ ! -f "$OPTIM_WAKE_FILE" ]; do
                sleep 5
            done
            rm -f "$OPTIM_WAKE_FILE"
        fi
        sleep 30
    done
}

# ── HOT INTAKE WATCHER ───────────────────────────────────────
intake_watcher_inotify() {
    inotifywait -m -r -e close_write,moved_to \
        "$BOOKS_DIR" "$CODE_DIR" "$REFERENCE_DIR" 2>/dev/null \
    | while IFS=' ' read -r dir _events fname; do
        local fp="${dir}${fname}"
        [ -f "$fp" ]     || continue
        ext_supported "$fp" || continue
        sleep 1
        process_intake_file "$fp"
        wake_optimizer
    done
}

intake_watcher_poll() {
    local snapshot=""
    while true; do
        local current
        current=$(find "$BOOKS_DIR" "$CODE_DIR" "$REFERENCE_DIR" \
            -type f 2>/dev/null | sort | md5sum | cut -d' ' -f1)
        if [ "$current" != "$snapshot" ]; then
            snapshot="$current"
            local c; c=$(process_all_intake)
            [ "$c" -gt 0 ] && wake_optimizer
        fi
        sleep 10
    done
}

start_intake_watcher() {
    if $HAS_INOTIFY; then
        intake_watcher_inotify &
    else
        intake_watcher_poll &
    fi
    WATCHER_PID=$!
}

# ── REASONING ENGINE (grep-based) ────────────────────────────
search_file() {
    local filepath="$1" query="$2"
    [ -f "$filepath" ] || return
    local count=0 term
    for term in $query; do
        [ "${#term}" -gt 3 ] || continue
        local hit
        hit=$(grep -i -A4 "$term" "$filepath" 2>/dev/null | head -15)
        [ -n "$hit" ] || continue
        printf '  %s\n' "$hit"
        count=$((count+1))
        [ "$count" -ge 3 ] && break
    done
}

reason_respond() {
    local query="$1"
    local tier="${CACHE_REASONING_TIER:-CPU_ONLY}"
    local found=false

    printf '[%s RESPONSE]\n' "$tier"

    # Encyclopedia overlay is checked FIRST — validated GGUF interpretations.
    local er
    er=$(encyclopedia_search "$query")
    if [ -n "$er" ]; then
        printf 'From Encyclopedia:\n%s\n' "$er"
        found=true
    fi

    # Fall through to base knowledge files
    local dr tr ar jr
    dr=$(search_file "$DICTIONARY" "$query")
    tr=$(search_file "$THESAURUS"  "$query")
    ar=$(search_file "$ALMANAC"    "$query")
    jr=$(search_file "$JOURNAL"    "$query")

    if [ -n "$dr" ]; then printf 'From Dictionary:\n%s\n' "$dr"; found=true; fi
    if [ -n "$ar" ]; then printf 'From Almanac:\n%s\n'    "$ar"; found=true; fi
    if [ -n "$tr" ]; then printf 'From Thesaurus:\n%s\n'  "$tr"; found=true; fi
    if [ -n "$jr" ]; then printf 'From Journal:\n%s\n'    "$jr"; found=true; fi

    $found || printf "No matching entries for '%s'. Drop files into intake/books/ to expand knowledge.\n" "$query"
}

# ── GREETING / SESSION PROFILE ───────────────────────────────
build_and_output_greeting() {
    local offset_str="$1" intake_count="$2"
    local D="═══════════════════════════════════════════════════════"
    local gpu_block prev_block intake_block

    if [ "${CACHE_GPU_AVAILABLE:-false}" = true ]; then
        gpu_block="  GPU      : ${CACHE_GPU_NAME}
  VRAM     : ${CACHE_GPU_VRAM_TOTAL_GB}GB total / ${CACHE_GPU_VRAM_FREE_GB}GB free
  GPU Load : ${CACHE_GPU_LOAD_PCT}%"
    else
        gpu_block="  GPU      : ${CACHE_GPU_NAME:-not detected}"
    fi

    [ -n "$offset_str" ] && prev_block="
PREVIOUS SESSION
  Offline for : $offset_str"

    [ "${intake_count:-0}" -gt 0 ] && intake_block="
INTAKE PROCESSED
  Files read this boot : $intake_count
  (see journal.txt for details)"

    local greeting="
$D
   SYSTEM PROFILE — AUTOGENERATED FOR THIS SESSION
$D

SESSION
  Started  : $(ts_now)
  Ms/mid   : $(ts_ms_mid)ms since midnight
${prev_block:-}

PROCESSOR
  CPU      : ${CACHE_CPU_BRAND:-Unknown}
  Cores    : ${CACHE_CPU_CORES_PHYSICAL:-?} physical / ${CACHE_CPU_CORES_LOGICAL:-?} logical
  Speed    : ${CACHE_CPU_FREQ_MHZ:-?} MHz
  Arch     : ${CACHE_CPU_ARCH:-?}
  OS       : ${CACHE_CPU_OS:-?}

MEMORY
  RAM      : ${CACHE_RAM_TOTAL_GB:-?}GB total / ${CACHE_RAM_AVAIL_GB:-?}GB free
  Swap     : ${CACHE_RAM_SWAP_GB:-?}GB

$gpu_block

REASONING TIER
  Mode     : ${CACHE_REASONING_TIER:-CPU_ONLY}
  CPU      : ACTIVE
  GPU      : $([ "${CACHE_GPU_AVAILABLE:-false}" = true ] && echo ACTIVE || echo NOT AVAILABLE)
${intake_block:-}

ACTIVE DOCUMENTS
  dictionary.txt      : $DOC_DICT
  thesaurus.txt       : $DOC_THES
  almanac.txt         : $DOC_ALMA
  journal.txt         : $DOC_JOUR

  hardware.cache    → $([ -f "$HARDWARE_CACHE" ] && echo EXISTS || echo NEW)
  AIsh.hiberfile    → $([ -f "$HIBERFILE"       ] && echo EXISTS || echo NEW)
  session_profile   → session_profile.txt
  intake folder     → ./intake/

$D
What is your query?
$D
"
    printf '%s\n' "$greeting" > "$SESSION_PROFILE"
    while IFS= read -r line; do chat "$line"; done <<< "$greeting"
    GREETING_CACHE="$greeting"
}

GREETING_CACHE=""

# ── LOG WATCHER — picks up #AI> USER> from Java session log ──
SESSION_LOG_WATCHER_PID=""

log_watcher() {
    [ -f "$SESSION_LOG" ] || touch "$SESSION_LOG"
    local pos
    pos=$(wc -c < "$SESSION_LOG" 2>/dev/null || echo 0)
    while true; do
        local sz
        sz=$(wc -c < "$SESSION_LOG" 2>/dev/null || echo 0)
        if [ "$sz" -gt "$pos" ]; then
            local new_lines
            new_lines=$(tail -c "+$((pos+1))" "$SESSION_LOG" 2>/dev/null)
            pos=$sz
            while IFS= read -r line; do
                printf '%s' "$line" | grep -q "GODDESS>"     && continue
                printf '%s' "$line" | grep -q "API_OVERRIDE" && continue
                if printf '%s' "$line" | grep -q "#AI> USER>"; then
                    local prompt
                    prompt=$(printf '%s' "$line" | sed 's/.*#AI> USER>//' | xargs)
                    handle_query "$prompt"
                fi
            done <<< "$new_lines"
        fi
        sleep 0.2
    done
}

start_log_watcher() {
    log_watcher &
    SESSION_LOG_WATCHER_PID=$!
}

# ── QUERY HANDLER ────────────────────────────────────────────
handle_query() {
    local query
    query=$(printf '%s' "$1" | xargs)
    [ -z "$query" ] && return

    case "${query,,}" in
        exit|quit|hibernate|bye) do_shutdown; return ;;
        status|profile|info)
            status "STATE: STATUS_REPORT"
            while IFS= read -r line; do chat "$line"; done <<< "$GREETING_CACHE"
            status "STATE: IDLE | AWAITING INPUT"
            return ;;
        help|"?")
            chat "Commands: status | exit/quit | help"
            chat "Type any query to search the knowledge base."
            chat "Drop files into intake/books/ — they load without restart."
            return ;;
    esac

    processing
    status "STATE: PROCESSING | REASONING"

    ctx_append "user" "$query"
    QUERY_COUNT=$((QUERY_COUNT+1))

    local response
    response=$(reason_respond "$query")

    ctx_append "assistant" "${response:0:300}"
    journal_append "QUERY: ${query:0:80} | RESPONSE LEN: ${#response} chars"

    while IFS= read -r line; do
        [ -n "$line" ] && chat "$line"
    done <<< "$response"

    # ── GGUF ENCYCLOPEDIA — user input event ─────────────────────
    # Write gate open only during this block.
    if [ -n "$GGUF_FILE" ] && [ -n "$LLAMA_BIN" ]; then
        GGUF_WRITE_ALLOWED=true
        local enc_response
        enc_response=$(gguf_enrich_query "$query")
        # Also enrich notable words found in the query
        for w in $query; do
            [ "${#w}" -ge 6 ] && gguf_enrich_word "$(printf '%s' "$w" | tr '[:upper:]' '[:lower:]')"
        done
        GGUF_WRITE_ALLOWED=false
        if [ -n "$enc_response" ]; then
            chat "Encyclopedia:"
            while IFS= read -r line; do
                [ -n "$line" ] && chat "  $line"
            done <<< "$enc_response"
        fi
    fi
    # ─────────────────────────────────────────────────────────────

    status "STATE: IDLE | AWAITING INPUT"

    wake_optimizer
}

# ── SHUTDOWN ─────────────────────────────────────────────────
WATCHER_PID=""
OPTIM_PID=""

do_shutdown() {
    status "STATE: SHUTDOWN | SAVING"
    [ -n "$WATCHER_PID"             ] && kill "$WATCHER_PID"             2>/dev/null || true
    [ -n "$SESSION_LOG_WATCHER_PID" ] && kill "$SESSION_LOG_WATCHER_PID" 2>/dev/null || true
    [ -n "$OPTIM_PID"               ] && kill "$OPTIM_PID"               2>/dev/null || true

    # ── GGUF ENCYCLOPEDIA — shutdown event ───────────────────────
    # Final enrichment pass before state is saved.
    # Write gate open only during this block.
    if [ -n "$GGUF_FILE" ] && [ -n "$LLAMA_BIN" ]; then
        GGUF_WRITE_ALLOWED=true
        gguf_shutdown_enrichment
        GGUF_WRITE_ALLOWED=false
    fi
    # ─────────────────────────────────────────────────────────────

    save_hiberfile
    journal_append "=== SESSION ENDED === queries:$QUERY_COUNT"
    status "STATE: OFFLINE"
    chat "Session hibernated. Goodbye."
    rm -f "$OPTIM_WAKE_FILE"
    exit 0
}

trap 'do_shutdown' INT TERM

# ══════════════════════════════════════════════════════════════
#   STARTUP SEQUENCE
# ══════════════════════════════════════════════════════════════
SESSION_START_UNIX=$(ts_unix)
status "STATE: BOOTING | INITIALIZING"

# Detect permissions first — gates all write operations
detect_permissions
if [ "$ELEVATED" = false ]; then
    chat "WARN: Running in restricted mode — knowledge evolution disabled."
    chat "      Tool execution, vocabulary updates, and file writes are read-only."
fi

init_documents
init_intake
journal_append "=== GODDESS API SHELL STARTUP ==="

# Record permission level in persona so AI is aware of its own constraints
persona_set_key "PERMISSION_MODE" "$([ "$ELEVATED" = true ] && echo elevated || echo restricted)"
persona_set_key "LAST_STARTUP" "$(ts_now)"

# Hardware
status "STATE: BOOTING | HARDWARE SCAN"
if load_hardware_cache; then
    journal_append "Hardware cache valid — skipped rescan"
else
    scan_and_save_hardware
    journal_append "Hardware scan complete"
fi

# Intake
status "STATE: BOOTING | INTAKE"
INTAKE_COUNT=$(process_all_intake)

# Hibernate
OFFSET_STR=""
if load_hiberfile; then
    OFFSET_STR=$(offline_duration)
    journal_append "Resumed from hibernation — offline $OFFSET_STR"
else
    journal_append "Fresh session — no previous state"
fi

# Start background workers
start_intake_watcher
start_log_watcher
optimization_loop &
OPTIM_PID=$!

# ── GGUF ENCYCLOPEDIA — startup event ────────────────────────
# Write gate open only during this block.
if find_gguf && find_llama_bin; then
    chat "Encyclopedia found: $(basename "$GGUF_FILE") — enriching knowledge base..."
    GGUF_WRITE_ALLOWED=true
    gguf_startup_enrichment
    GGUF_WRITE_ALLOWED=false
elif find_gguf && ! find_llama_bin; then
    chat "WARN: $(basename "$GGUF_FILE") found but no llama.cpp binary detected."
    chat "      Install llama-cli to enable GGUF encyclopedia."
fi
# ─────────────────────────────────────────────────────────────

# Greet
build_and_output_greeting "$OFFSET_STR" "$INTAKE_COUNT"
status "STATE: IDLE | AWAITING INPUT"

# ══════════════════════════════════════════════════════════════
#   MAIN STDIN LOOP
#   Listens for direct pipe input from GoddessMatrix.
#   The log_watcher background job handles #AI> USER> lines.
# ══════════════════════════════════════════════════════════════
while IFS= read -r raw; do
    raw=$(printf '%s' "$raw" | xargs)
    [ -z "$raw" ]       && continue
    [ "$raw" = "exit" ] && do_shutdown
    handle_query "$raw"
done
