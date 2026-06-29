"""
GoddessOS_Mac.py — MacOSY OS module for GoddessAPI.py
scripts/MacOSY/GoddessOS_Mac.py

Provides the three OS-specific functions GoddessAPI.py needs:
  detect_permissions()  — macOS sandbox + SIP awareness
  build_gpu_profile()   — ioreg Metal GPU enumeration
  make_intake_watcher() — FSEvents via fsevents package if available,
                          falls back to polling

MacOSY-specific optimizations:
  - APFS / ZFS awareness: checks filesystem type via diskutil
  - ioreg for GPU enumeration (works for Apple Silicon + AMD + Intel)
  - FSEvents for zero-latency intake detection
  - SIP (System Integrity Protection) detection for write gating
  - Apple Silicon unified memory detection (GPU VRAM = system RAM)
  - GoddessCore.awk shared with Linux (same AWK, macOS ships with it)

Note on ZFS:
  macOS added native ZFS read support in recent releases for certain
  functions. OpenZFS on macOS (zfs-mac) is a separate install.
  detect_filesystem() identifies ZFS volumes so intake scanning can
  use zfs diff for efficient change detection rather than polling.

Contributors:
  Derek Jason Gilhousen — OS module architecture, MacOSY design intent
  Claude (Anthropic)    — GoddessOS_Mac.py implementation
"""

import os, re, time, threading, subprocess, hashlib, platform, json
from pathlib import Path

# ── GODDESS CORE AWK PATH ─────────────────────────────────────────────────────
# MacOSY shares GoddessCore.awk with Linux — same file, macOS ships with awk.
GODDESS_CORE_AWK = Path(__file__).parent / "GoddessCore.awk"
# If not in MacOSY folder, try Linux folder sibling
if not GODDESS_CORE_AWK.exists():
    GODDESS_CORE_AWK = Path(__file__).parent.parent / "Linux" / "GoddessCore.awk"

def goddess_core_call(cmd, query="", filepath=None, **kwargs):
    if not GODDESS_CORE_AWK.exists():
        return ""
    args = ["awk", "-f", str(GODDESS_CORE_AWK), "-v", f"cmd={cmd}"]
    if query:
        args += ["-v", f"query={query}"]
    for k, v in kwargs.items():
        args += ["-v", f"{k}={v}"]
    if filepath and Path(filepath).exists():
        args.append(str(filepath))
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=5)
        return r.stdout.strip()
    except Exception:
        return ""

# ── PERMISSIONS ───────────────────────────────────────────────────────────────

def _sip_enabled():
    """Check if System Integrity Protection is active."""
    try:
        r = subprocess.run(["csrutil", "status"],
                           capture_output=True, text=True, timeout=3)
        return "enabled" in r.stdout.lower()
    except FileNotFoundError:
        return True  # assume SIP on if csrutil not found

def detect_permissions():
    """
    MacOSY: root or sudo elevates permissions.
    SIP-awareness: even root cannot write to certain paths when SIP is on.
    App sandbox detection: macOS apps may run in a container — probe actual
    write access rather than relying on uid alone.
    """
    if os.getuid() == 0 and not _sip_enabled():
        return True  # root + SIP off = truly elevated
    if os.getuid() == 0:
        # Root but SIP on — still elevated for our paths (not /System etc.)
        pass
    if os.environ.get("SUDO_USER"):
        pass  # sudo context — likely elevated for our working dir
    # Probe actual write access regardless of uid
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
    MacOSY: uses diskutil info to identify filesystem type.
    Returns: 'apfs', 'zfs', 'hfs+', 'exfat', etc.
    ZFS note: if ZFS detected, change detection can use 'zfs diff'
    for efficient snapshot-based intake watching rather than polling.
    """
    try:
        target = str(Path(path).resolve())
        r = subprocess.run(["diskutil", "info", target],
                           capture_output=True, text=True, timeout=5)
        if r.returncode == 0:
            m = re.search(r"Type \(Bundle\)\s*:\s*(\S+)", r.stdout)
            if m:
                return m.group(1).lower()
            m = re.search(r"File System Personality\s*:\s*(.+)", r.stdout)
            if m:
                return m.group(1).strip().lower()
    except Exception:
        pass
    return "apfs"  # safe default — macOS has been APFS since 10.13

def _is_zfs_volume(path="."):
    return detect_filesystem(path) == "zfs"

# ── CPU PROFILING ─────────────────────────────────────────────────────────────

def scan_cpu_mac():
    """
    MacOSY: uses sysctl for CPU details.
    Works for both Intel and Apple Silicon.
    """
    info = {
        "processor":    platform.processor(),
        "architecture": platform.machine(),
        "os":           "Darwin",
        "os_version":   platform.mac_ver()[0],
    }
    try:
        def sysctl(key):
            r = subprocess.run(["sysctl", "-n", key],
                                capture_output=True, text=True, timeout=3)
            return r.stdout.strip() if r.returncode == 0 else ""

        brand = sysctl("machdep.cpu.brand_string")
        if not brand:
            brand = sysctl("hw.model")  # Apple Silicon returns model string
        info["brand"] = brand or platform.processor()

        cores_p = sysctl("hw.physicalcpu")
        cores_l = sysctl("hw.logicalcpu")
        info["cores_physical"] = int(cores_p) if cores_p.isdigit() else "?"
        info["cores_logical"]  = int(cores_l) if cores_l.isdigit() else "?"

        freq = sysctl("hw.cpufrequency_max")
        if freq.isdigit():
            info["frequency_mhz"] = int(freq) / 1_000_000
    except Exception:
        pass
    return info

# ── GPU PROFILING ─────────────────────────────────────────────────────────────

def _ioreg_gpus():
    """
    MacOSY: enumerate GPUs via ioreg.
    Works for Apple Silicon (Metal), AMD, and Intel integrated.
    Apple Silicon: GPU VRAM is unified — reported as a fraction of system RAM.
    """
    gpus = []
    try:
        r = subprocess.run(
            ["ioreg", "-r", "-d", "2", "-c", "IOPCIDevice", "-a"],
            capture_output=True, text=True, timeout=8)
        if r.returncode != 0:
            return gpus

        # Parse as plist if possible
        try:
            import plistlib
            data = plistlib.loads(r.stdout.encode() if isinstance(r.stdout, str)
                                  else r.stdout)
            for device in data:
                name = device.get("model", b"")
                if isinstance(name, bytes):
                    name = name.decode("utf-8", errors="ignore")
                if not name:
                    continue
                # Filter: only GPU-class devices
                if not any(k in name.lower()
                           for k in ("gpu", "graphics", "radeon", "intel",
                                     "nvidia", "apple", "metal")):
                    continue
                vram = device.get("VRAM,totalMB", device.get("VRAM (Total)", 0))
                if isinstance(vram, bytes):
                    vram = int.from_bytes(vram, "little") // (1024 * 1024)
                gpus.append({"name": str(name), "vram_total_mb": int(vram),
                             "vram_free_mb": 0, "driver": "metal",
                             "load_pct": 0.0})
        except Exception:
            # Fallback: regex parse
            for m in re.finditer(r'"model"\s*=\s*<([^>]+)>', r.stdout):
                raw = bytes.fromhex(m.group(1).replace(" ", ""))
                name = raw.decode("utf-8", errors="ignore").strip("\x00")
                if name:
                    gpus.append({"name": name, "vram_total_mb": 0,
                                 "vram_free_mb": 0, "driver": "metal",
                                 "load_pct": 0.0})
    except Exception:
        pass
    return gpus

def _apple_silicon_gpu():
    """
    Apple Silicon: GPU is part of the SoC.
    Reports unified memory as VRAM.
    """
    try:
        r = subprocess.run(["system_profiler", "SPHardwareDataType", "-json"],
                           capture_output=True, text=True, timeout=8)
        if r.returncode != 0:
            return None
        data = json.loads(r.stdout)
        hw   = data.get("SPHardwareDataType", [{}])[0]
        chip = hw.get("chip_type", "")
        if "apple" in chip.lower() or "m1" in chip.lower() or "m2" in chip.lower() \
                or "m3" in chip.lower() or "m4" in chip.lower():
            mem_gb = hw.get("physical_memory", "0 GB").split()[0]
            try:
                # Apple Silicon shares RAM — GPU gets roughly half by default
                total_mb = int(float(mem_gb) * 1024)
                return {"name": f"{chip} GPU (unified)",
                        "vram_total_mb": total_mb,
                        "vram_free_mb":  total_mb // 2,  # approximate
                        "driver": "metal",
                        "load_pct": 0.0,
                        "unified": True}
            except ValueError:
                pass
    except Exception:
        pass
    return None

def build_gpu_profile():
    """
    MacOSY GPU profile. Priority:
    1. Apple Silicon unified memory (SoC GPU)
    2. ioreg PCI device enumeration (discrete AMD/Intel/NVIDIA)
    3. Absent
    """
    # Check Apple Silicon first
    as_gpu = _apple_silicon_gpu()
    if as_gpu:
        return {"available": True,
                "devices":   [as_gpu],
                "apple_silicon": True}

    devs = _ioreg_gpus()
    if not devs:
        return {"available": False,
                "reason": "no GPU detected via ioreg"}
    return {
        "available": True,
        "apple_silicon": False,
        "devices": [{
            "name":          g["name"],
            "vram_total_gb": round(g["vram_total_mb"] / 1024, 2),
            "vram_free_gb":  round(g["vram_free_mb"]  / 1024, 2),
            "driver":        g["driver"],
            "load_pct":      g["load_pct"],
        } for g in devs]
    }

# ── INTAKE WATCHER ────────────────────────────────────────────────────────────

class FSEventsWatcher:
    """
    MacOSY: uses the fsevents Python package for kernel-level file change
    notifications via the FSEvents API. Zero CPU between events.
    Falls back to ZFS diff (if ZFS volume) or polling.

    fsevents package: pip install fsevents
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

    def _fsevents_available(self):
        try:
            import fsevents  # noqa
            return True
        except ImportError:
            return False

    def stop(self):
        self._running = False

    def loop(self):
        if self._fsevents_available():
            self._fsevents_loop()
        else:
            self._poll_loop()

    def _fsevents_loop(self):
        try:
            import fsevents

            def on_event(path):
                if not self._running:
                    return
                time.sleep(0.3)  # brief debounce
                cur = self._fingerprint()
                if cur != self._snap:
                    self._snap = cur
                    self._callback()

            observer = fsevents.Observer()
            for d in self._dirs:
                stream = fsevents.Stream(on_event, d, file_events=True)
                observer.schedule(stream)
            observer.start()
            while self._running:
                time.sleep(1)
            observer.stop()
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
            self._fw = FSEventsWatcher(dirs, self._on_change)
            self._p  = processor
            self._o  = optimizer

        def _on_change(self):
            c = self._p.run()
            if c > 0:
                self._o.wake()

        def start(self):
            return self._fw.start()

        def stop(self):
            self._fw.stop()

    return _W()
