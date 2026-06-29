# ════════════════════════════════════════════════════════════════════════════
# GoddessCore.ps1  (GCore-W.ps1)
# scripts/Windows/GoddessCore.ps1
#
# Windows shared knowledge processing core.
# Same command interface as GoddessCore.awk (Linux) and GoddessCore.zfs (Mac).
# Called by GoddessAPI.bat and GoddessOS_Windows.py via PowerShell subprocess.
#
# USAGE:
#   powershell -NoProfile -File GoddessCore.ps1 -cmd search -query "maps" -filepath dict.txt
#   pwsh       -NoProfile -File GoddessCore.ps1 -cmd kv_get -key CPU_BRAND  -filepath hw.cache
#   <response> | powershell -File GoddessCore.ps1 -cmd distill -query "word" -maxChars 32
#
# COMMANDS (identical interface to GoddessCore.awk):
#   search       Search file for query terms. Returns matching lines indented.
#   blocks       Parse [TAG]...[/TAG] blocks. Returns matching blocks, --- separated.
#   creatures    Search creatures.txt by env filter and query. Returns ENV|NAME|BEHAVIOR.
#   distill      Read stdin response, return densest phrase <= maxChars.
#   kv_get       Return value for key from key=value file.
#   score_block  Score stdin block relevance against query. Returns integer.
#
# WINDOWS-SPECIFIC:
#   - Handles CRLF line endings transparently
#   - UTF-8 output via [Console]::OutputEncoding
#   - Works with PowerShell 5.1 (Windows built-in) and PowerShell 7+
#   - No external dependencies — pure PowerShell
#
# PLATFORM: Windows only. Linux/Mac use GoddessCore.awk / GoddessCore.zfs.
#
# Contributors:
#   Derek Jason Gilhousen — Windows architecture, cross-platform core design
#   Claude (Anthropic)    — GoddessCore.ps1 implementation
# ════════════════════════════════════════════════════════════════════════════

[CmdletBinding()]
param(
    [string]$cmd        = "help",
    [string]$query      = "",
    [string]$tag        = "ENTRY",
    [string]$key        = "",
    [int]   $maxChars   = 32,
    [string]$envFilter  = "land,air,any",
    [int]   $minTermLen = 4,
    [string]$filepath   = ""
)

# UTF-8 output so protocol tags transmit correctly
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ── TERM SPLITTING ────────────────────────────────────────────────────────────
function Get-QueryTerms {
    param([string]$q)
    $q.ToLower() -split '\s+' | Where-Object { $_.Length -ge $minTermLen }
}

# ── SCORE BLOCK ───────────────────────────────────────────────────────────────
function Get-BlockScore {
    param([string]$text, [string]$q)
    if (-not $q) { return 1 }
    $score = 0
    $low   = $text.ToLower()
    foreach ($term in (Get-QueryTerms $q)) {
        if ($low.Contains($term)) { $score += 2 }
    }
    return $score
}

# ── HELPERS ───────────────────────────────────────────────────────────────────
function Get-BlockField {
    param([string[]]$lines, [string]$fieldName)
    foreach ($line in $lines) {
        if ($line -match "^${fieldName}=(.*)") {
            return $Matches[1].Trim()
        }
    }
    return ""
}

function Read-InputFile {
    # Returns lines from $filepath if set and exists, else stdin
    if ($filepath -and (Test-Path $filepath)) {
        Get-Content -Path $filepath -Encoding UTF8 |
            ForEach-Object { $_ -replace "`r", "" }  # strip CRLF
    } else {
        $input | ForEach-Object { $_ -replace "`r", "" }
    }
}

# ══════════════════════════════════════════════════════════════════════════════
# COMMAND: search
# ══════════════════════════════════════════════════════════════════════════════
function Invoke-Search {
    $terms   = Get-QueryTerms $query
    $count   = 0
    $maxHits = 6

    foreach ($line in (Read-InputFile)) {
        if ($count -ge $maxHits) { break }
        $low = $line.ToLower()
        foreach ($term in $terms) {
            if ($low.Contains($term)) {
                $out = if ($line.Length -gt 200) { $line.Substring(0,200) + "..." } else { $line }
                Write-Output ("  " + $out)
                $count++
                break
            }
        }
    }
}

# ══════════════════════════════════════════════════════════════════════════════
# COMMAND: blocks
# ══════════════════════════════════════════════════════════════════════════════
function Invoke-Blocks {
    $openTag  = "[$tag]"
    $closeTag = "[/$tag]"
    $inBlock  = $false
    $block    = [System.Collections.Generic.List[string]]::new()

    foreach ($line in (Read-InputFile)) {
        if ($line -eq $openTag) {
            $inBlock = $true
            $block.Clear()
        } elseif ($line -eq $closeTag -and $inBlock) {
            $blockText = $block -join "`n"
            $score     = Get-BlockScore $blockText $query
            if ($score -gt 0 -or -not $query) {
                Write-Output $blockText
                Write-Output "---"
            }
            $inBlock = $false
            $block.Clear()
        } elseif ($inBlock) {
            $block.Add($line)
        }
    }
}

# ══════════════════════════════════════════════════════════════════════════════
# COMMAND: creatures
# ══════════════════════════════════════════════════════════════════════════════
function Invoke-Creatures {
    $allowedEnvs = $envFilter.ToLower() -split ","
    $inBlock     = $false
    $block       = [System.Collections.Generic.List[string]]::new()
    $found       = 0

    foreach ($line in (Read-InputFile)) {
        if ($found -ge 3) { break }

        if ($line -eq "[CREATURE]") {
            $inBlock = $true
            $block.Clear()
        } elseif ($line -eq "[/CREATURE]" -and $inBlock) {
            $lines   = $block.ToArray()
            $cEnv    = Get-BlockField $lines "ENVIRONMENT"
            $cName   = Get-BlockField $lines "NAME"
            $cRealm  = Get-BlockField $lines "REALM"
            $cBehav  = Get-BlockField $lines "BEHAVIOR"

            # Environment filter
            $envOk = $false
            if ($cRealm -eq "any") { $envOk = $true }
            if (-not $envOk) {
                $creatureEnvs = $cEnv.ToLower() -split ","
                foreach ($ae in $allowedEnvs) {
                    foreach ($ce in $creatureEnvs) {
                        if ($ce.Trim() -eq $ae.Trim()) { $envOk = $true; break }
                    }
                    if ($envOk) { break }
                }
            }

            if ($envOk) {
                $score = Get-BlockScore ($block -join " ") $query
                if ($score -gt 0 -or $cRealm -eq "any") {
                    # First sentence of behavior
                    $first = ($cBehav -split '[.!?]')[0].Trim()
                    Write-Output "${cEnv}|${cName}|${first}"
                    $found++
                }
            }
            $inBlock = $false
            $block.Clear()
        } elseif ($inBlock) {
            $block.Add($line)
        }
    }
}

# ══════════════════════════════════════════════════════════════════════════════
# COMMAND: distill
# Atmospheric distillation — thin air at game altitude.
# ══════════════════════════════════════════════════════════════════════════════
function Invoke-Distill {
    # Collect stdin and strip structural lines
    $clean = [System.Text.StringBuilder]::new()
    foreach ($line in $input) {
        $l = ($line -replace "`r","").Trim()
        if ($l -match '^\[' -or $l -match '^From ' -or $l -match '^#' -or $l -eq "") {
            continue
        }
        if ($clean.Length -gt 0) { [void]$clean.Append(" ") }
        [void]$clean.Append($l)
    }
    $fullText = $clean.ToString()

    # Split into sentence candidates
    $candidates = $fullText -split '[.!?;]' |
                  ForEach-Object { $_.Trim() } |
                  Where-Object   { $_.Length -ge 4 }

    $best      = ""
    $bestScore = [int]::MinValue
    $bestLen   = [int]::MaxValue

    foreach ($c in $candidates) {
        $score = Get-BlockScore $c $query
        if ($c.Length -le $maxChars) { $score++ }

        if ($score -gt $bestScore -or ($score -eq $bestScore -and $c.Length -lt $bestLen)) {
            $best      = $c
            $bestScore = $score
            $bestLen   = $c.Length
        }
    }

    # Return if fits
    if ($best -and $best.Length -le $maxChars) {
        Write-Output $best
        return
    }

    # Truncate at word boundary
    if ($best) {
        $words  = $best -split ' '
        $result = ""
        foreach ($w in $words) {
            $test = if ($result) { "$result $w" } else { $w }
            if ($test.Length -le $maxChars) { $result = $test } else { break }
        }
        if ($result) { Write-Output $result; return }
    }

    # Last resort
    if ($fullText.Length -gt 0) {
        Write-Output $fullText.Substring(0, [Math]::Min($maxChars, $fullText.Length))
    }
}

# ══════════════════════════════════════════════════════════════════════════════
# COMMAND: kv_get
# ══════════════════════════════════════════════════════════════════════════════
function Invoke-KvGet {
    foreach ($line in (Read-InputFile)) {
        if ($line -match '^#' -or $line -notmatch '=') { continue }
        $eq  = $line.IndexOf('=')
        $k   = $line.Substring(0, $eq).Trim()
        $v   = $line.Substring($eq + 1).Trim()
        if ($k -eq $key) {
            Write-Output $v
            return
        }
    }
    # Key not found — empty output (same as awk version)
    Write-Output ""
}

# ══════════════════════════════════════════════════════════════════════════════
# COMMAND: score_block
# ══════════════════════════════════════════════════════════════════════════════
function Invoke-ScoreBlock {
    $text  = ($input | ForEach-Object { $_ -replace "`r","" }) -join "`n"
    Write-Output (Get-BlockScore $text $query)
}

# ── DISPATCH ──────────────────────────────────────────────────────────────────
switch ($cmd.ToLower()) {
    "search"      { Invoke-Search     }
    "blocks"      { Invoke-Blocks     }
    "creatures"   { Invoke-Creatures  }
    "distill"     { Invoke-Distill    }
    "kv_get"      { Invoke-KvGet      }
    "score_block" { Invoke-ScoreBlock }
    default {
        Write-Output "GoddessCore.ps1 — Windows shared core"
        Write-Output "Commands: search  blocks  creatures  distill  kv_get  score_block"
        Write-Output "Usage:    powershell -File GoddessCore.ps1 -cmd <command> [options] [-filepath file]"
    }
}
