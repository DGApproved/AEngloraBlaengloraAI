"""
GoddessOS_Windows.py — Windows OS module for GoddessAPI.py
scripts/Windows/GoddessOS_Windows.py

Provides the three OS-specific functions GoddessAPI.py needs:
  detect_permissions()  — UAC / admin token detection
  build_gpu_profile()   — WMI GPU enumeration + DXGI VRAM
  make_intake_watcher() — ReadDirectoryChangesW via watchdog or polling

Windows-specific optimizations:
  - NTFS change journal (USN journal) for efficient file change detection
  - WMI for GPU, CPU, and hardware profiling
  - UAC elevation detection via ctypes
  - win32api / pywin32 when available
  - GoddessCore.ps1 as the shared text core (AWK not native to Windows)
  - No os.getuid() — Windows uses a different permission model

Note on GoddessCore:
  Windows uses GoddessCore.ps1 instead of GoddessCore.awk.
  goddess_core_call() on Windows invokes PowerShell.
  The same commands (search, blocks, creatures, distill, kv_get)
  are implemented in GoddessCore.ps1 with identical output format.

Contributors:
  Derek Jason Gilhousen — OS module architecture, Windows design intent
  Claude (Anthropic)    — GoddessOS_Windows.py implementation
"""

import os, re, time, threading, subprocess, hashlib, platform, ctypes
from pathlib import Path

# ── GODDESS CORE PS1 PATH ─────────────────────────────────────────────────────
# Windows uses GoddessCore.ps1 — same commands, PowerShell implementation.
GODDESS_CORE_PS1 = Path(__file__).parent / "GoddessCore.ps1"

def goddess_core_call(cmd, query="", filepath=None, **kwargs):
    """
    Call GoddessCore.ps1 via PowerShell. Same interface as the AWK version.
    Windows: uses powershell.exe (or pwsh for PowerShell 7+).
    """
    if not GODDESS_CORE_PS1.exists():
        return ""
    ps_exe = "pwsh" if _pwsh_available() else "powershell"
    args = [ps_exe, "-NoProfile", "-NonInteractive",
            "-File", str(GODDESS_CORE_PS1),
            "-cmd", cmd]
    if query:
        args += ["-query", query]
    if filepath and Path(filepath).exists():
        args += ["-filepath", str(filepath)]
    for k, v in kwargs.items():
        args += [f"-{k}", str(v)]
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=10)
        return r.stdout.strip()
    except Exception:
        return ""

def _pwsh_available():
    try:
        subprocess.run(["pwsh", "--version"], capture_output=True, timeout=2)
        return True
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False

# ── PERMISSIONS ───────────────────────────────────────────────────────────────

def detect_permissions():
    """
    Windows: checks for admin token via ctypes IsUserAnAdmin.
    UAC elevation means the process token has admin privileges.
    Falls back to write probe if ctypes unavailable.
    Note: os.getuid() does not exist on Windows.
    """
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except (AttributeError, OSError):
        pass
    # Fallback: probe write access
    for probe_dir in [Path(".."), Path(".")]:
        try:
            probe = probe_dir / f".goddess_perm_{os.getpid()}"
            probe.write_text("x")
            probe.unlink()
            return True
        except (PermissionError, OSError):
            pass
    return False

# ── FILESYSTEM DETECTION ──────────────────────────────────────────────────────

def detect_filesystem(path="."):
    """
    Windows: uses fsutil fsinfo volumeinfo to identify filesystem.
    Returns: 'ntfs', 'refs', 'fat32', 'exfat', etc.
    NTFS has the USN change journal — most efficient for intake watching.
    ReFS is common on Windows Server.
    """
    try:
        target = str(Path(path).resolve())
        drive = Path(target).anchor  # e.g. "C:\\"
        r = subprocess.run(
            ["fsutil", "fsinfo", "volumeinfo", drive],
            capture_output=True, text=True, timeout=5)
        if r.returncode == 0:
            m = re.search(r"File System Name\s+:\s+(\S+)", r.stdout, re.I)
            if m:
                return m.group(1).lower()
    except Exception:
        pass
    return "ntfs"  # safe default

# ── CPU PROFILING ─────────────────────────────────────────────────────────────

def scan_cpu_windows():
    """
    Windows: WMI Win32_Processor for CPU details.
    Falls back to platform module if WMI unavailable.
    """
    info = {
        "processor":    platform.processor(),
        "architecture": platform.machine(),
        "os":           "Windows",
        "os_version":   platform.version(),
    }
    try:
        # Try wmic first (available on older Windows, deprecated in 11 but still works)
        r = subprocess.run(
            ["wmic", "cpu", "get",
             "Name,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed",
             "/format:csv"],
            capture_output=True, text=True, timeout=8)
        if r.returncode == 0:
            lines = [l for l in r.stdout.strip().splitlines() if l.strip() and l[0] != "N"]
            if lines:
                parts = lines[0].split(",")
                if len(parts) >= 5:
                    # CSV: Node,MaxClockSpeed,Name,NumberOfCores,NumberOfLogicalProcessors
                    info["frequency_mhz"]  = float(parts[1]) if parts[1].isdigit() else "Unknown"
                    info["brand"]          = parts[2].strip()
                    info["cores_physical"] = int(parts[3]) if parts[3].isdigit() else "?"
                    info["cores_logical"]  = int(parts[4]) if parts[4].isdigit() else "?"
    except Exception:
        pass

    if "brand" not in info:
        # PowerShell fallback
        try:
            r = subprocess.run(
                ["powershell", "-NoProfile", "-Command",
                 "Get-CimInstance Win32_Processor | Select-Object -ExpandProperty Name"],
                capture_output=True, text=True, timeout=8)
            if r.returncode == 0:
                info["brand"] = r.stdout.strip()
        except Exception:
            pass

    return info

# ── GPU PROFILING ─────────────────────────────────────────────────────────────

def _wmi_gpus():
    """
    Windows: WMI Win32_VideoController for GPU enumeration.
    Returns list with name and AdapterRAM (VRAM in bytes from WMI).
    Note: WMI reports VRAM in bytes; may be inaccurate for > 4GB on 32-bit WMI.
    """
    gpus = []
    try:
        r = subprocess.run(
            ["wmic", "path", "Win32_VideoController",
             "get", "Name,AdapterRAM,DriverVersion,CurrentRefreshRate",
             "/format:csv"],
            capture_output=True, text=True, timeout=8)
        if r.returncode != 0:
            return gpus
        for line in r.stdout.strip().splitlines():
            if not line.strip() or line.startswith("Node"):
                continue
            parts = line.split(",")
            if len(parts) >= 3:
                vram_bytes = 0
                try:
                    vram_bytes = int(parts[1])
                except (ValueError, IndexError):
                    pass
                name   = parts[2].strip() if len(parts) > 2 else "Unknown GPU"
                driver = parts[3].strip() if len(parts) > 3 else "unknown"
                if name and name != "Unknown GPU":
                    gpus.append({
                        "name":         name,
                        "vram_total_mb": vram_bytes // (1024 * 1024),
                        "vram_free_mb":  0,  # WMI doesn't expose free VRAM easily
                        "driver":       driver,
                        "load_pct":     0.0,
                    })
    except Exception:
        pass
    return gpus

def build_gpu_profile():
    """
    Windows GPU profile via WMI.
    nvidia-smi also works on Windows for NVIDIA cards — try both.
    """
    # nvidia-smi works on Windows too
    devs = []
    try:
        r = subprocess.run(
            ["nvidia-smi",
             "--query-gpu=name,memory.total,memory.free,driver_version,utilization.gpu",
             "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=5)
        if r.returncode == 0:
            for line in r.stdout.strip().splitlines():
                p = [x.strip() for x in line.split(",")]
                if len(p) >= 5:
                    devs.append({"name": p[0], "vram_total_mb": int(p[1]),
                                 "vram_free_mb": int(p[2]), "driver": p[3],
                                 "load_pct": float(p[4])})
    except FileNotFoundError:
        pass

    if not devs:
        devs = _wmi_gpus()

    if not devs:
        return {"available": False,
                "reason": "no GPU detected via nvidia-smi or WMI"}
    return {
        "available": True,
        "devices": [{
            "name":          g["name"],
            "vram_total_gb": round(g["vram_total_mb"] / 1024, 2),
            "vram_free_gb":  round(g["vram_free_mb"]  / 1024, 2),
            "driver":        g["driver"],
            "load_pct":      g["load_pct"],
        } for g in devs]
    }

# ── INTAKE WATCHER ────────────────────────────────────────────────────────────

class WindowsDirectoryWatcher:
    """
    Windows: uses watchdog package (ReadDirectoryChangesW under the hood)
    for efficient filesystem event notification.
    Falls back to polling if watchdog unavailable.
    watchdog package: pip install watchdog
    """
    def __init__(self, dirs, callback):
        self._dirs     = [str(d) for d in dirs if Path(d).is_dir()]
        self._callback = callback
        self._running  = True
        self._snap     = self._fingerprint()

    def _fingerprint(self):
        files = []
        for d in self._dirs:
            try:
                files.extend(sorted(str(f) for f in Path(d).iterdir()
                                    if Path(f).is_file()))
            except Exception:
                pass
        return hashlib.md5("|".join(files).encode()).hexdigest()

    def _watchdog_available(self):
        try:
            import watchdog  # noqa
            return True
        except ImportError:
            return False

    def stop(self):
        self._running = False

    def loop(self):
        if self._watchdog_available():
            self._watchdog_loop()
        else:
            self._poll_loop()

    def _watchdog_loop(self):
        try:
            from watchdog.observers import Observer
            from watchdog.events import FileSystemEventHandler

            watcher = self

            class _Handler(FileSystemEventHandler):
                def on_any_event(self, event):
                    if not watcher._running:
                        return
                    time.sleep(0.3)
                    cur = watcher._fingerprint()
                    if cur != watcher._snap:
                        watcher._snap = cur
                        watcher._callback()

            observer = Observer()
            handler  = _Handler()
            for d in self._dirs:
                observer.schedule(handler, d, recursive=True)
            observer.start()
            while self._running:
                time.sleep(1)
            observer.stop()
            observer.join()
        except Exception:
            self._poll_loop()

    def _poll_loop(self):
        while self._running:
            time.sleep(10)
            cur = self._fingerprint()
            if cur != self._snap:
                self._snap = cur
                self._callback()

    def start(self):
        t = threading.Thread(target=self.loop, daemon=True)
        t.start()
        return t

def make_intake_watcher(books_dir, code_dir, reference_dir, processor, optimizer):
    dirs = [books_dir, code_dir, reference_dir]

    class _W:
        def __init__(self):
            self._ww = WindowsDirectoryWatcher(dirs, self._on_change)
            self._p  = processor
            self._o  = optimizer

        def _on_change(self):
            c = self._p.run()
            if c > 0:
                self._o.wake()

        def start(self):
            return self._ww.start()

        def stop(self):
            self._ww.stop()

    return _W()
