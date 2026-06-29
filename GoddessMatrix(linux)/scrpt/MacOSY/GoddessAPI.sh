#!/bin/bash
# ════════════════════════════════════════════════════════════════════════════
# GoddessAPI.sh  —  Goddess Matrix Shell API
#
# Standalone bash implementation of the Goddess API protocol.
# Same directory structure and tag protocol as GoddessAPI.py.
#
# ATMOSPHERIC RESPONSE MODEL:
#   GGUF core      — llamafile/llama.cpp inference if present
#                    (the planet core: dense, foundational)
#   .sh process    — this script: land and sea layer
#                    (shapes and routes the knowledge)
#   .txt files     — dictionary, almanac, encyclopedia, persona
#                    (creatures of the land and sea)
#   Response       — distilled air: refined from all layers below
#
#   In-game chat   — high altitude: thin air ≤32 chars
#                    [GAME_QUERY:text] triggers this path
#   Matrix chat    — sea level: full response, no length limit
#
# PROTOCOL TAGS:
#   [STATUS] msg          — status bar
#   [CHAT] msg            — chat display
#   [PROCESSING]          — working indicator
#   [GAME_CHAT:text]      — ≤32 char game response
#   [GAME_NARRATE:text]   — ambient floating text
#
# Contributors:
#   Derek Jason Gilhousen — architecture, atmospheric analogy
#   Claude (Anthropic)    — GoddessAPI.sh implementation
# ════════════════════════════════════════════════════════════════════════════

# Bash version guard — macOS ships bash 3.2 on Intel (GPLv2 era).
# brew install bash gives 5.x. We warn but continue — all features used
# here are compatible with 3.2 except associative arrays (not used).
if [ "${BASH_VERSINFO[0]:-3}" -lt 4 ]; then
    echo "[CHAT] WARN: bash ${BASH_VERSION} detected. Some features work best with bash 5+." >&2
    echo "[CHAT]       brew install bash  then  sudo chsh -s /usr/local/bin/bash" >&2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="$SCRIPT_DIR"
DGAPI="$BASE/dgapi"
SYSTEM_DIR="$DGAPI/system"
DATAS_DIR="$DGAPI/datas"
VIRTUAL_DIR="$DGAPI/virtual"
INTAKE_DIR="$DGAPI/intake"
HARDWARE_CACHE="$SYSTEM_DIR/hardware.cache"
HIBERFILE="$SYSTEM_DIR/AIsh.hiberfile"
JOURNAL="$DATAS_DIR/journal.txt"
DICTIONARY="$DATAS_DIR/dictionary.txt"
ALMANAC="$DATAS_DIR/almanac.txt"
THESAURUS="$DATAS_DIR/thesaurus.txt"
ENCYCLOPEDIA="$VIRTUAL_DIR/encyclopedia.txt"
RELIGION="$VIRTUAL_DIR/religion.txt"
PERSONA="$VIRTUAL_DIR/persona.txt"
OUTPUT_VOCAB="$VIRTUAL_DIR/output_vocabulary.txt"
OUTPUT_VOCAB_SH="$VIRTUAL_DIR/output_vocabulary_sh.txt"
GAME_EVENTS="$SYSTEM_DIR/game_events.txt"

# GoddessCore.zfs — ZFS-aware shared processing core (MacOSY)
GODDESS_CORE="$(dirname "$SCRIPT_DIR")/scripts/MacOSY/GoddessCore.zfs"
[ -f "$GODDESS_CORE" ] || GODDESS_CORE="$SCRIPT_DIR/GoddessCore.zfs"
# Final fallback: GoddessCore.awk from Linux folder (same commands, no ZFS)
[ -f "$GODDESS_CORE" ] || GODDESS_CORE="$(dirname "$SCRIPT_DIR")/scripts/Linux/GoddessCore.awk"

mkdir -p "$SYSTEM_DIR" "$DATAS_DIR" "$VIRTUAL_DIR" \
         "$INTAKE_DIR/books" "$INTAKE_DIR/code" "$INTAKE_DIR/reference"

# ── PROTOCOL ─────────────────────────────────────────────────────────────────
status()    { echo "[STATUS] $*";       }
chat()      { echo "[CHAT] $*";         }
processing(){ echo "[PROCESSING]";      }
game_chat() { echo "[GAME_CHAT:$*]";    }
narrate()   { echo "[GAME_NARRATE:$*]"; }
journal_append() {
    echo "[$(date -Iseconds 2>/dev/null || date)] $*" >> "$JOURNAL" 2>/dev/null || true
}

# ── VOCABULARY ────────────────────────────────────────────────────────────────
init_vocab() {
    [ -f "$OUTPUT_VOCAB" ] && return
    cat > "$OUTPUT_VOCAB" << 'VOCABEOF'
# output_vocabulary.txt — shared .py and .sh
# sh: prefix = shell only  py: prefix = python only  no prefix = shared
TONE=analytical
GREETING_STYLE=formal
sh:RESPONSE_MAX_LINES=20
py:RESPONSE_MAX_LINES=40
BORDER_CHAR=═
DIVIDER_WIDTH=55
VOCABEOF
}

vocab_get() {
    local key="$1" default="${2:-}"
    local path="$OUTPUT_VOCAB_SH"
    [ -f "$path" ] || path="$OUTPUT_VOCAB"
    [ -f "$path" ] || { echo "$default"; return; }
    local val
    val=$(grep -m1 "^sh:${key}=" "$path" 2>/dev/null | cut -d= -f2-)
    [ -z "$val" ] && val=$(grep -m1 "^${key}=" "$path" 2>/dev/null | cut -d= -f2-)
    echo "${val:-$default}"
}

# ── HARDWARE ──────────────────────────────────────────────────────────────────
CPU_BRAND="Unknown"; CPU_CORES="?"; RAM_TOTAL="?"; GPU_NAME="none"
GPU_AVAILABLE="false"; REASONING_TIER="CPU_ONLY"
POWER_SOURCE="unknown"; BATTERY_PCT="?"; THERMAL_STATE="nominal"

load_hardware() {
    # MacOSY: GoddessCore.zfs for cache reads, sysctl for live fallback.
    if [ -f "$HARDWARE_CACHE" ]; then
        if [ -f "$GODDESS_CORE" ]; then
            CPU_BRAND=$(bash "$GODDESS_CORE" kv_get --key CPU_BRAND        --filepath "$HARDWARE_CACHE" 2>/dev/null)
            CPU_CORES=$(bash "$GODDESS_CORE" kv_get --key CPU_CORES_LOGICAL --filepath "$HARDWARE_CACHE" 2>/dev/null)
            RAM_TOTAL=$(bash "$GODDESS_CORE" kv_get --key RAM_TOTAL_GB      --filepath "$HARDWARE_CACHE" 2>/dev/null)
            GPU_NAME=$(bash  "$GODDESS_CORE" kv_get --key GPU_NAME          --filepath "$HARDWARE_CACHE" 2>/dev/null)
            GPU_AVAILABLE=$(bash "$GODDESS_CORE" kv_get --key GPU_AVAILABLE    --filepath "$HARDWARE_CACHE" 2>/dev/null)
            REASONING_TIER=$(bash "$GODDESS_CORE" kv_get --key REASONING_TIER  --filepath "$HARDWARE_CACHE" 2>/dev/null)
        else
            while IFS='=' read -r k v; do
                [[ "$k" =~ ^# ]] && continue
                case "$k" in
                    CPU_BRAND)         CPU_BRAND="$v"      ;;
                    CPU_CORES_LOGICAL) CPU_CORES="$v"      ;;
                    RAM_TOTAL_GB)      RAM_TOTAL="$v"      ;;
                    GPU_NAME)          GPU_NAME="$v"       ;;
                    GPU_AVAILABLE)     GPU_AVAILABLE="$v"  ;;
                    REASONING_TIER)    REASONING_TIER="$v" ;;
                esac
            done < "$HARDWARE_CACHE"
        fi
    fi

    # sysctl fallback — live read when cache absent or fields empty
    [ -z "$CPU_BRAND" ] &&         CPU_BRAND=$(sysctl -n machdep.cpu.brand_string 2>/dev/null                  || sysctl -n hw.model 2>/dev/null                  || echo "Unknown")
    [ -z "$CPU_CORES" ] &&         CPU_CORES=$(sysctl -n hw.logicalcpu 2>/dev/null || echo "?")
    if [ -z "$RAM_TOTAL" ] || [ "$RAM_TOTAL" = "?" ]; then
        local mem_bytes; mem_bytes=$(sysctl -n hw.memsize 2>/dev/null || echo 0)
        RAM_TOTAL=$(( mem_bytes / 1073741824 ))
    fi
    [ -z "$GPU_NAME"       ] && GPU_NAME="none"
    [ -z "$GPU_AVAILABLE"  ] && GPU_AVAILABLE="false"
    [ -z "$REASONING_TIER" ] && REASONING_TIER="CPU_ONLY"

    load_power_state
}

load_power_state() {
    # pmset: battery percentage and power source
    if command -v pmset &>/dev/null; then
        local pmout; pmout=$(pmset -g batt 2>/dev/null | head -2)
        echo "$pmout" | grep -q "AC Power"      && POWER_SOURCE="AC"
        echo "$pmout" | grep -q "Battery Power" && POWER_SOURCE="battery"
        local pct; pct=$(echo "$pmout" | grep -o "[0-9]*%" | head -1 | tr -d '%')
        [ -n "$pct" ] && BATTERY_PCT="${pct}%"
    fi

    # Thermal level — Intel: machdep.xcpm.cpu_thermal_level
    # Apple Silicon: no direct equivalent but hw.cputhrottlefactor works
    local thermal
    thermal=$(sysctl -n machdep.xcpm.cpu_thermal_level 2>/dev/null            || sysctl -n hw.cputhrottlefactor 2>/dev/null            || echo "")
    if [ -z "$thermal" ] || [ "$thermal" = "0" ]; then
        THERMAL_STATE="nominal"
    elif [ "$thermal" -gt 50 ] 2>/dev/null; then
        THERMAL_STATE="throttled"
    else
        THERMAL_STATE="warm"
    fi
}


find_gguf_runtime() {
    for bin in "$BASE/llamafile" \
               "/usr/local/bin/llamafile" \
               "/opt/homebrew/bin/llamafile" \
               "$HOME/.local/bin/llamafile" \
               "$HOME/Library/Application Support/llamafile" \
               "/Applications/llamafile" \
               "$BASE/llama-cli" "$BASE/main" "$BASE/llama.cpp/main" \
               "/usr/local/bin/llama-cli" \
               "/opt/homebrew/bin/llama-cli"; do
        [ -x "$bin" ] && GGUF_BINARY="$bin" && break
    done
    GGUF_MODEL=$(find "$BASE" -maxdepth 2 -name "*.gguf" 2>/dev/null | head -1)
}

gguf_query() {
    local prompt="$1"
    [ -z "$GGUF_BINARY" ] || [ -z "$GGUF_MODEL" ] && return 0
    "$GGUF_BINARY" -m "$GGUF_MODEL" -p "$prompt" -n 64 \
        --temp 0.7 --repeat-penalty 1.1 -s 0 2>/dev/null \
        | grep -v "^llama\|^system\|^\[" | head -5 || true
}

# ── CREATURES.TXT — the living bestiary ──────────────────────────────────────
# Creatures are read by both .sh and .py runtimes.
# Land creatures (environment=land or land,sea) contribute to .sh responses.
# Air creatures contribute to all responses regardless of runtime.
# Their BEHAVIOR text provides humidity — cross-environment response richness.

CREATURES="$DATAS_DIR/creatures.txt"

creatures_init() {
    [ -f "$CREATURES" ] && return
    printf '# creatures.txt — Living Bestiary of the Knowledge World
# Not captured. Not contained.
' > "$CREATURES"
}

creatures_search() {
    local query="$1"
    [ -f "$CREATURES" ] || return 0
    if [ -f "$GODDESS_CORE" ]; then
        awk -f "$GODDESS_CORE" -v cmd=creatures             -v query="$query" -v env_filter="land,air,any"             "$CREATURES" 2>/dev/null | head -3
    else
        local in_block=0 block="" name="" env=""
        while IFS= read -r line; do
            if [ "$line" = "[CREATURE]" ]; then
                in_block=1; block=""; name=""; env=""
            elif [ "$line" = "[/CREATURE]" ]; then
                if [ $in_block -eq 1 ] && [ -n "$block" ]; then
                    env=$(echo "$block" | grep "^ENVIRONMENT=" | cut -d= -f2-)
                    case "$env" in *land*|*air*|*any*) ;; *) in_block=0; block=""; continue ;; esac
                    for term in $query; do
                        [ "${#term}" -lt 4 ] && continue
                        if echo "$block" | grep -qi "$term"; then
                            name=$(echo "$block" | grep "^NAME=" | cut -d= -f2-)
                            behav=$(echo "$block" | grep "^BEHAVIOR=" | cut -d= -f2- | head -1)
                            echo "$env|$name|$behav"; break
                        fi
                    done
                fi
                in_block=0; block=""
            elif [ $in_block -eq 1 ]; then
                block="$block
$line"
            fi
        done < "$CREATURES"
    fi
}

creatures_spawn() {
    # Add a new creature to creatures.txt
    local name="$1" environment="$2" realm="$3" behavior="$4" traits="$5"
    [ -f "$CREATURES" ] && grep -q "NAME=$name" "$CREATURES" 2>/dev/null && return
    creatures_init()
    local today; today=$(date +%Y-%m-%d 2>/dev/null || echo "unknown")
    cat >> "$CREATURES" << CREEOF

[CREATURE]
NAME=$name
ENVIRONMENT=$environment
REALM=$realm
BEHAVIOR=$behavior
TRAITS=$traits
FIRST_SEEN=$today
STATUS=theorized
[/CREATURE]
CREEOF
    journal_append "CREATURE SPAWNED: $name | env:$environment realm:$realm"
}

creature_flavor() {
    # Returns one line of ambient creature presence for a query
    # This is the humidity — land creature breath in the .sh response air
    local query="$1"
    local result; result=$(creatures_search "$query" | head -1)
    [ -z "$result" ] && return 0
    local env; env=$(echo "$result" | cut -d'|' -f1)
    local name; name=$(echo "$result" | cut -d'|' -f2)
    local behav; behav=$(echo "$result" | cut -d'|' -f3-)
    # First sentence of behavior only
    local first; first=$(echo "$behav" | sed 's/[.!?].*//' | head -1)
    [ -n "$first" ] && echo "[${env^^}] $first."
}

# ── TXT KNOWLEDGE SEARCH — creatures of the land ─────────────────────────────
search_file() {
    # Delegates to GoddessCore.awk when available; falls back to grep.
    local filepath="$1" query="$2" max_results="${3:-3}"
    [ -f "$filepath" ] || return 0
    if [ -f "$GODDESS_CORE" ]; then
        awk -f "$GODDESS_CORE" -v cmd=search -v query="$query"             -v min_term_len=4 "$filepath" 2>/dev/null | head -"$max_results"
    else
        local count=0
        for term in $query; do
            [ ${#term} -lt 4 ] && continue
            while IFS= read -r line; do
                echo "  $line"
                count=$((count+1))
                [ $count -ge $max_results ] && return
            done < <(grep -in "$term" "$filepath" 2>/dev/null | head -2)
        done
    fi
}

search_encyclopedia() {
    local query="$1"
    [ -f "$ENCYCLOPEDIA" ] || return 0
    if [ -f "$GODDESS_CORE" ]; then
        awk -f "$GODDESS_CORE" -v cmd=blocks -v tag=ENTRY             -v query="$query" "$ENCYCLOPEDIA" 2>/dev/null             | grep -E "^(WORD|TOPIC|DEFINITION|TEXT)=" | head -4 | sed 's/^/  /'
    else
        local in_block=0 block=""
        while IFS= read -r line; do
            if [ "$line" = "[ENTRY]" ]; then in_block=1; block=""
            elif [ "$line" = "[/ENTRY]" ]; then
                if [ $in_block -eq 1 ] && [ -n "$block" ]; then
                    for term in $query; do
                        [ ${#term} -lt 4 ] && continue
                        if echo "$block" | grep -qi "$term"; then
                            echo "$block" | grep -E "^(WORD|TOPIC|DEFINITION|TEXT)=" | head -2 | sed 's/^/  /'
                            break
                        fi
                    done
                fi
                in_block=0; block=""
            elif [ $in_block -eq 1 ]; then block="$block $line"; fi
        done < "$ENCYCLOPEDIA"
    fi
}

# ── REASONING ENGINE — land and sea ──────────────────────────────────────────
sh_respond() {
    local query="$1"
    echo "[$REASONING_TIER RESPONSE]"

    # Layer 1: Encyclopedia overlay (validated, promoted from religion.txt)
    local enc; enc=$(search_encyclopedia "$query")
    if [ -n "$enc" ]; then
        echo "From Encyclopedia:"
        echo "$enc"
    fi

    # Layer 2: Base knowledge files (the txt creatures)
    for pair in "From Dictionary:$DICTIONARY" \
                "From Almanac:$ALMANAC" \
                "From Thesaurus:$THESAURUS"; do
        local label="${pair%%:*}" fpath="${pair#*:}"
        local result; result=$(search_file "$fpath" "$query" 3)
        [ -n "$result" ] && echo "$label" && echo "$result"
    done

    # Layer 3: Persona (AI self-model)
    if [ -f "$PERSONA" ]; then
        local psnip; psnip=$(grep -i "$query" "$PERSONA" 2>/dev/null | head -1 | sed 's/^/  /')
        [ -n "$psnip" ] && echo "From Persona:" && echo "$psnip"
    fi

    # Layer 4: GGUF core (deepest — only if available)
    if [ -n "$GGUF_BINARY" ] && [ -n "$GGUF_MODEL" ]; then
        local ctx; ctx=$(cat "$ENCYCLOPEDIA" "$DICTIONARY" 2>/dev/null | head -50 | tr '\n' ' ')
        local gguf_out; gguf_out=$(gguf_query "Context: $ctx Query: $query Answer briefly:")
        if [ -n "$gguf_out" ]; then
            echo "From GGUF Core:"
            echo "$gguf_out" | head -3 | sed 's/^/  /'
        fi
    fi

    # Creature flavor — humidity in the response air.
    # Land and air creatures contribute their ambient presence.
    local flavor; flavor=$(creature_flavor "$query")
    [ -n "$flavor" ] && echo "$flavor"
}

# ── ATMOSPHERIC DISTILLATION — thin air at elevation ─────────────────────────
# Game altitude: ≤32 chars.
# Extracts the densest meaningful phrase from the full multi-layer response.
# 1. Strip structural labels
# 2. Split into sentence candidates
# 3. Score by query term density (more hits = higher altitude survivability)
# 4. Return shortest scoring candidate ≤ max_chars
# 5. Fallback: truncate at word boundary

game_distill() {
    local full_response="$1" query="$2" max_chars="${3:-32}"
    if [ -f "$GODDESS_CORE" ]; then
        echo "$full_response" | awk -f "$GODDESS_CORE"             -v cmd=distill -v query="$query" -v max_chars="$max_chars" 2>/dev/null
    else
        # Inline fallback when GoddessCore.awk not found
        local clean
        clean=$(echo "$full_response"             | grep -v '^\[' | grep -v '^From ' | grep -v '^#'             | sed 's/^[[:space:]]*//' | tr -s ' \t' ' ' | grep -v '^$')
        echo "$clean" | tr -s ' \n' ' ' | cut -c1-"${max_chars}"
    fi
}

# ── GAME QUERY HANDLER ────────────────────────────────────────────────────────
handle_game_query() {
    local query="$1"
    [ -z "$query" ] && return
    # Refresh thermal state before heavy processing — Mac throttles under load
    load_power_state
    if [ "$THERMAL_STATE" = "throttled" ]; then
        journal_append "THERMAL: throttled during GAME_QUERY — response may be slower"
    fi
    echo "$(date -Iseconds 2>/dev/null)|GAME_QUERY|${query:0:80}" >> "$GAME_EVENTS" 2>/dev/null || true
    journal_append "GAME_QUERY: ${query:0:80}"

    local full_response; full_response=$(sh_respond "$query")

    # Thin air distillation — ≤32 chars
    local distilled; distilled=$(game_distill "$full_response" "$query" 32)
    game_chat "$distilled"

    # Also narrate the first meaningful sentence (floats above avatar)
    local narr
    narr=$(echo "$full_response" \
        | grep -v '^\[' | grep -v '^From ' | grep -v '^#' \
        | grep -v '^$' | head -1 | cut -c1-80)
    [ -n "$narr" ] && narrate "$narr"
}

# ── NORMAL QUERY HANDLER ──────────────────────────────────────────────────────
handle_query() {
    local query="$1"
    [ -z "$query" ] && return
    processing
    status "STATE: PROCESSING | REASONING"
    journal_append "QUERY: ${query:0:80}"

    local response; response=$(sh_respond "$query")
    local max_lines; max_lines=$(vocab_get "RESPONSE_MAX_LINES" "20")

    echo "$response" | grep -v '^$' | head -"$max_lines" | while IFS= read -r line; do
        chat "$line"
    done

    status "STATE: IDLE | AWAITING INPUT"
    journal_append "RESPONSE LEN: ${#response} chars"
}

# ── STARTUP ───────────────────────────────────────────────────────────────────
startup() {
    status "STATE: BOOTING | SHELL INITIALIZING"

    [ -f "$JOURNAL" ]      || echo "# Journal — session log"          > "$JOURNAL"
    [ -f "$DICTIONARY" ]   || echo "# Dictionary — autogenerated"     > "$DICTIONARY"
    [ -f "$ALMANAC" ]      || echo "# Almanac — autogenerated"        > "$ALMANAC"
    [ -f "$THESAURUS" ]    || echo "# Thesaurus — autogenerated"      > "$THESAURUS"
    [ -f "$ENCYCLOPEDIA" ] || printf '# Encyclopedia Overlay\n'       > "$ENCYCLOPEDIA"
    [ -f "$RELIGION" ]     || printf '# Religion — Theory Queue\n'    > "$RELIGION"
    [ -f "$PERSONA" ]      || printf '# persona.txt\n'                > "$PERSONA"
    [ -f "$GAME_EVENTS" ]  || touch "$GAME_EVENTS"
    creatures_init
    init_vocab

    status "STATE: BOOTING | HARDWARE SCAN"
    load_hardware

    status "STATE: BOOTING | GGUF SCAN"
    find_gguf_runtime

    journal_append "=== GODDESS API SHELL STARTUP ==="
    journal_append "CPU: $CPU_BRAND ($CPU_CORES cores) | RAM: ${RAM_TOTAL}GB"
    if [ -n "$GGUF_BINARY" ] && [ -n "$GGUF_MODEL" ]; then
        journal_append "GGUF: $GGUF_BINARY | MODEL: $(basename "$GGUF_MODEL")"
    else
        journal_append "GGUF: not found — txt-only reasoning mode"
    fi

    local D="═══════════════════════════════════════════════════════"
    chat "$D"
    chat "   GODDESS API — SHELL RUNTIME"
    chat "$D"
    chat "  CPU     : $CPU_BRAND ($CPU_CORES cores)"
    chat "  RAM     : ${RAM_TOTAL}GB"
    chat "  GPU     : $GPU_NAME"
    chat "  GGUF    : $([ -n "$GGUF_MODEL" ] && basename "$GGUF_MODEL" || echo "not found")"
    chat "  Tier    : $REASONING_TIER"
    chat "  Power   : $POWER_SOURCE | Battery: $BATTERY_PCT | Thermal: $THERMAL_STATE"
    chat "$D"
    status "STATE: IDLE | AWAITING INPUT"
}

# ── MAIN LOOP ─────────────────────────────────────────────────────────────────
startup

while IFS= read -r line; do
    line="${line%%$'\r'}"
    [ -z "$line" ] && continue

    # Game query — high altitude, thin air distillation
    if [[ "$line" =~ ^\[GAME_QUERY:(.*)\]$ ]]; then
        handle_game_query "${BASH_REMATCH[1]}"
        continue
    fi

    case "${line,,}" in
        exit|quit|hibernate|bye)
            status "STATE: SHUTDOWN | SAVING"
            journal_append "=== SHELL SESSION ENDED ==="
            status "STATE: OFFLINE"
            chat "Session ended. Goodbye."
            exit 0
            ;;
        status|profile|info)
            chat "CPU: $CPU_BRAND | RAM: ${RAM_TOTAL}GB | Tier: $REASONING_TIER"
            status "STATE: IDLE | AWAITING INPUT"
            continue
            ;;
        help|\?)
            chat "Commands: status | exit/quit | help"
            chat "Type any query to search knowledge base."
            chat "[GAME_QUERY:text] for compressed game-chat response."
            continue
            ;;
    esac

    handle_query "$line"
done
