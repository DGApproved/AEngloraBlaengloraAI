@echo off
:: ════════════════════════════════════════════════════════════════════════════
:: GoddessAPI.bat — Windows Native Goddess API Launcher
:: scripts/Windows/GoddessAPI.bat
::
:: Three-path execution strategy:
::   1. Python available → launch GoddessAPI.py (preferred, full features)
::   2. Python absent    → delegate to GoddessAPI.ps1 (PowerShell native)
::   3. Neither          → error with install guidance
::
:: PROTOCOL TAGS: identical to Linux/Mac versions.
::   [STATUS] msg        — status bar update
::   [CHAT] msg          — chat display
::   [PROCESSING]        — working indicator
::   [GAME_CHAT:text]    — ≤32 char game response
::   [GAME_NARRATE:text] — ambient floating text
::
:: WINDOWS-SPECIFIC BEHAVIOUR:
::   - Detects Python in PATH, py launcher, conda environments
::   - Sets PYTHONIOENCODING=utf-8 for proper stdout protocol tag encoding
::   - Passes Windows script folder as GODDESS_SCRIPTS env var
::   - GoddessCore.ps1 used when Python absent (same commands as GoddessCore.awk)
::   - Handles Windows line endings in knowledge files via PowerShell pipeline
::
:: USAGE:
::   Double-click GoddessAPI.bat  — interactive session
::   GoddessAPI.bat               — same
::   GoddessAPI.bat --ps1         — force PowerShell path regardless of Python
::   GoddessAPI.bat --check       — detect environment and report
::
:: Contributors:
::   Derek Jason Gilhousen — Windows architecture, .bat design
::   Claude (Anthropic)    — GoddessAPI.bat implementation
:: ════════════════════════════════════════════════════════════════════════════

setlocal EnableDelayedExpansion

:: ── SCRIPT LOCATION ──────────────────────────────────────────────────────────
set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

:: Parent of scripts/Windows/ is the AI home directory
for %%A in ("%SCRIPT_DIR%\..") do set "SCRIPTS_ROOT=%%~fA"
for %%A in ("%SCRIPT_DIR%\..\..") do set "AI_HOME=%%~fA"

:: ── ENVIRONMENT VARIABLES FOR SUBPROCESSES ───────────────────────────────────
set "GODDESS_SCRIPTS=%SCRIPT_DIR%"
set "GODDESS_HOME=%AI_HOME%"
set "PYTHONIOENCODING=utf-8"
set "PYTHONUTF8=1"

:: ── ARGUMENT HANDLING ─────────────────────────────────────────────────────────
set "FORCE_PS1=0"
set "CHECK_ONLY=0"
if "%~1"=="--ps1"   set "FORCE_PS1=1"
if "%~1"=="--check" set "CHECK_ONLY=1"

:: ── PYTHON DETECTION ──────────────────────────────────────────────────────────
set "PYTHON_CMD="
set "PYTHON_FOUND=0"

if "%FORCE_PS1%"=="1" goto :USE_POWERSHELL

:: Try py launcher (Windows Python Launcher — most reliable on Windows)
where py >nul 2>&1
if %ERRORLEVEL%==0 (
    py --version >nul 2>&1
    if !ERRORLEVEL!==0 (
        set "PYTHON_CMD=py"
        set "PYTHON_FOUND=1"
    )
)

:: Try python3
if "%PYTHON_FOUND%"=="0" (
    where python3 >nul 2>&1
    if !ERRORLEVEL!==0 (
        python3 --version >nul 2>&1
        if !ERRORLEVEL!==0 (
            set "PYTHON_CMD=python3"
            set "PYTHON_FOUND=1"
        )
    )
)

:: Try python
if "%PYTHON_FOUND%"=="0" (
    where python >nul 2>&1
    if !ERRORLEVEL!==0 (
        python --version 2>&1 | findstr /i "Python 3" >nul
        if !ERRORLEVEL!==0 (
            set "PYTHON_CMD=python"
            set "PYTHON_FOUND=1"
        )
    )
)

:: Try conda python
if "%PYTHON_FOUND%"=="0" (
    if defined CONDA_PREFIX (
        if exist "%CONDA_PREFIX%\python.exe" (
            set "PYTHON_CMD=%CONDA_PREFIX%\python.exe"
            set "PYTHON_FOUND=1"
        )
    )
)

:: ── CHECK MODE ────────────────────────────────────────────────────────────────
if "%CHECK_ONLY%"=="1" (
    echo [CHAT] GoddessAPI Windows Environment Check
    echo [CHAT] ─────────────────────────────────────────
    if "%PYTHON_FOUND%"=="1" (
        echo [CHAT]   Python     : FOUND ^(%PYTHON_CMD%^)
        for /f "tokens=*" %%V in ('"%PYTHON_CMD%" --version 2^>^&1') do echo [CHAT]   Version    : %%V
    ) else (
        echo [CHAT]   Python     : NOT FOUND
    )
    where pwsh >nul 2>&1 && (
        echo [CHAT]   PowerShell : pwsh ^(PS7+^)
    ) || (
        where powershell >nul 2>&1 && echo [CHAT]   PowerShell : powershell ^(PS5^) || echo [CHAT]   PowerShell : NOT FOUND
    )
    if exist "%SCRIPT_DIR%\GoddessCore.ps1" (
        echo [CHAT]   GoddessCore: GoddessCore.ps1 FOUND
    ) else (
        echo [CHAT]   GoddessCore: GoddessCore.ps1 NOT FOUND
    )
    echo [CHAT]   AI Home    : %AI_HOME%
    echo [CHAT]   Scripts    : %SCRIPT_DIR%
    echo [STATUS] STATE: IDLE ^| ENVIRONMENT CHECK COMPLETE
    goto :END
)

:: ── PYTHON PATH: launch GoddessAPI.py ─────────────────────────────────────────
if "%PYTHON_FOUND%"=="1" (
    :: Look for GoddessAPI.py — check scripts/Windows/ first, then AI home
    set "PYFILE="
    if exist "%SCRIPT_DIR%\GoddessAPI.py"   set "PYFILE=%SCRIPT_DIR%\GoddessAPI.py"
    if not defined PYFILE (
        if exist "%AI_HOME%\GoddessAPI.py"  set "PYFILE=%AI_HOME%\GoddessAPI.py"
    )
    if not defined PYFILE (
        if exist "%SCRIPTS_ROOT%\GoddessAPI.py" set "PYFILE=%SCRIPTS_ROOT%\GoddessAPI.py"
    )

    if defined PYFILE (
        echo [STATUS] STATE: BOOTING ^| PYTHON PATH
        cd /d "%AI_HOME%"
        "%PYTHON_CMD%" "%PYFILE%"
        goto :END
    )
    echo [CHAT] WARN: GoddessAPI.py not found — falling back to PowerShell path
)

:USE_POWERSHELL
:: ── POWERSHELL PATH: launch GoddessAPI.ps1 ────────────────────────────────────
set "PS_EXE=powershell"
where pwsh >nul 2>&1 && set "PS_EXE=pwsh"

set "PS1FILE="
if exist "%SCRIPT_DIR%\GoddessAPI.ps1" set "PS1FILE=%SCRIPT_DIR%\GoddessAPI.ps1"

if defined PS1FILE (
    echo [STATUS] STATE: BOOTING ^| POWERSHELL PATH
    cd /d "%AI_HOME%"
    "%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1FILE%"
    goto :END
)

:: ── NEITHER AVAILABLE ─────────────────────────────────────────────────────────
echo [STATUS] STATE: ERROR ^| NO RUNTIME FOUND
echo [CHAT] ERROR: Neither Python 3 nor GoddessAPI.ps1 found.
echo [CHAT]
echo [CHAT] To resolve:
echo [CHAT]   Option 1: Install Python 3 from https://python.org
echo [CHAT]             (check "Add to PATH" during install)
echo [CHAT]   Option 2: Place GoddessAPI.ps1 in %SCRIPT_DIR%
echo [CHAT]
echo [CHAT] After installing Python, run: GoddessAPI.bat --check
echo [CHAT] to verify the environment.
pause

:END
endlocal
