#!/usr/bin/env python3
"""Read-only privacy inventory and performance snapshot; never applies a debloat list.

Use --serial with an already connected/authorized adb device, or --snapshot to
analyze previously collected query results offline. No third-party dependencies.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


REFERENCE = "https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/"
# Only these fixed commands can reach adb. No target, file, or package from a
# report is ever interpolated into a remote shell command.
QUERIES = {
    "model": "getprop ro.product.model",
    "device": "getprop ro.product.device",
    "android": "getprop ro.build.version.release",
    "sdk": "getprop ro.build.version.sdk",
    "adb_secure": "getprop ro.adb.secure",
    "packages": "pm list packages --user 0",
    "disabled": "pm list packages -d --user 0",
    "memory": "cat /proc/meminfo",
    "home_memory": "dumpsys meminfo io.freemytv:home",
    "frames": "dumpsys gfxinfo io.freemytv:home",
}
# Candidates are evidence for a review, not a new automatic disable policy.
CATALOGUE = {
    "tv.samba.ssm.ui": ("acr", "Samba viewing recognition", "examples/tcl-g10-google-tv/debloat.txt"),
    "com.tcl.logkit": ("diagnostics", "TCL log collection", "examples/tcl-g08-eu-android12/debloat.txt"),
    "com.tcl.smartalexa": ("voice", "Alexa; disabling removes that assistant", "examples/tcl-g08-eu-android12/debloat.txt"),
    "com.google.android.feedback": ("review", "Feedback sender; not all Google telemetry", "tools/privacy.py"),
    "com.android.tv.feedbackconsent": ("review", "Feedback consent/report flow; review together with feedback sender", "tools/privacy.py"),
    "com.google.android.tv.remotecontrol.logging": ("review", "Remote logging candidate; distinct from Remote Service", "tools/privacy.py"),
    "com.google.android.apps.tv.netoscope": ("review", "Network diagnostics; verify role on this firmware", "tools/privacy.py"),
    "com.tcl.tv.plus": ("review", "TCL TV+ content; owner may use it", "examples/tcl-g10-google-tv/debloat.txt"),
    "com.tcl.smartlink.core": ("review", "Bundled installer candidate; inspect before any change", "examples/tcl-g10-google-tv/debloat.txt"),
    "com.tcl.ttvs": ("keep", "TIAS/input overlay dependency on G08; not universally telemetry", "examples/tcl-g08-eu-android12/debloat.txt"),
    "com.tcl.interactive": ("keep", "Input/source overlay dependency on G08", "examples/tcl-g08-eu-android12/debloat.txt"),
    "com.tcl.suspension": ("keep", "Source-menu QuickPanel", "examples/tcl-g08-eu-android12/debloat.txt"),
    "com.tcl.guard": ("keep", "Disabling triggered repeated service-start attempts on G08", "examples/tcl-g08-eu-android12/debloat.txt"),
}
PACKAGE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+")
ERROR = re.compile(r"exception|permission denial|permission denied|not found|unknown command|error:", re.I)


def collect(adb, serial):
    results = {}
    for name, command in QUERIES.items():
        try:
            run = subprocess.run([adb, "-s", serial, "shell", command],
                                 capture_output=True, text=True, timeout=20, check=False)
            # Never persist raw output or errors: they can contain device IDs.
            results[name] = run.stdout if run.returncode == 0 else None
        except subprocess.TimeoutExpired:
            results[name] = None
    return results


def packages(raw):
    if not isinstance(raw, str) or ERROR.search(raw):
        return None
    found = set()
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        if not line.startswith("package:") or not PACKAGE.fullmatch(line[8:]):
            return None
        found.add(line[8:])
    return found


def policy(source):
    """Read the existing literal lists; fail visibly if their declaration changes."""
    source = re.sub(r"/\*.*?\*/|//[^\n]*", "", source, flags=re.S)
    lists = {}
    for name, pattern in (
        ("DEBLOAT", r"String\[\] DEBLOAT\s*=\s*\{(.*?)\};"),
        ("PROTECTED", r"Set<String> PROTECTED\s*=\s*new HashSet<>\(Arrays.asList\((.*?)\)\);"),
    ):
        match = re.search(pattern, source, re.S)
        if not match:
            raise ValueError("Cannot read HomeMode " + name + "; update the audit parser")
        lists[name] = set(re.findall(r'"([A-Za-z0-9_.]+)"', match[1]))
    return lists


def scalar(raw):
    if not isinstance(raw, str) or ERROR.search(raw):
        return None
    value = raw.strip()
    # Bounded model/platform fields only; never serial, fingerprint or network.
    return value if value and len(value) <= 100 and all(c.isprintable() for c in value) else None


def number(raw, expression):
    if not isinstance(raw, str) or ERROR.search(raw):
        return None
    match = re.search(expression, raw, re.M)
    return int(match[1].replace(",", "")) if match else None


def report(results, rules):
    installed = packages(results.get("packages"))
    disabled = packages(results.get("disabled"))
    # A blank full inventory or inconsistent responses must not imply absence.
    if not installed or disabled is None or not disabled <= installed:
        installed = disabled = None
    rows = []
    for pkg, (category, note, source) in CATALOGUE.items():
        state = "unknown" if installed is None else (
            "absent" if pkg not in installed else "disabled" if pkg in disabled else "enabled")
        existing = "protected" if pkg in rules["PROTECTED"] else (
            "already targeted" if pkg in rules["DEBLOAT"] else "review only")
        rows.append({"package": pkg, "state": state, "policy": existing,
                     "category": category, "evidence": note, "source": REFERENCE + source})
    mem = results.get("memory")
    frames = results.get("frames")
    return {
        "schema": 1,
        "scope": "Read-only snapshot; no changes, no proof of tracking traffic or speedup.",
        "device": {k: scalar(results.get(k)) for k in ("model", "device", "android", "sdk")},
        "inventory_verified": installed is not None,
        "adb_authentication_required": {"1": True, "0": False}.get(scalar(results.get("adb_secure"))),
        "packages": rows,
        "performance": {
            "mem_total_kib": number(mem, r"^MemTotal:\s+(\d+)\s+kB"),
            "mem_available_kib": number(mem, r"^MemAvailable:\s+(\d+)\s+kB"),
            "swap_free_kib": number(mem, r"^SwapFree:\s+(\d+)\s+kB"),
            "home_pss_kib": number(results.get("home_memory"), r"^\s*TOTAL PSS:\s*([\d,]+)"),
            "frames_since_reset": number(frames, r"^\s*Total frames rendered:\s*([\d,]+)"),
            "janky_frames_since_reset": number(frames, r"^\s*Janky frames:\s*([\d,]+)"),
            "limits": "Cumulative gfxinfo counters, not a controlled benchmark or input latency. "
                      "Null means unavailable. Memory varies with foreground app and cache state.",
        },
        "manual_review": [
            "Use the supported system Privacy/Ads control to delete advertising ID where available.",
            "Review optional usage/diagnostics and actual Samba consent; status was not queried.",
            "Keep Play Protect, updates, Play services, input and remote services enabled.",
            "DNS filtering needs a separate network decision; this tool reads no network settings.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--serial", help="Already connected ADB target; never stored in report")
    mode.add_argument("--snapshot", type=Path, help="Offline JSON mapping query names to output strings/null")
    parser.add_argument("--adb", default="adb", help="Path to installed platform-tools adb")
    parser.add_argument("--out", type=Path, help="New sanitized JSON file (refuses overwrite); default stdout")
    args = parser.parse_args()
    try:
        if args.serial and (args.serial.startswith("-") or not re.fullmatch(r"[A-Za-z0-9_.:\[\]-]+", args.serial)):
            raise ValueError("Invalid ADB target")
        rules = policy((Path(__file__).resolve().parents[1] / "app/src/io/freemytv/HomeMode.java").read_text())
        results = json.loads(args.snapshot.read_text()) if args.snapshot else collect(args.adb, args.serial)
        if not isinstance(results, dict) or any(k not in QUERIES for k in results):
            raise ValueError("Snapshot must contain only documented query names")
        if any(v is not None and not isinstance(v, str) for v in results.values()):
            raise ValueError("Snapshot values must be strings or null")
        output = report(results, rules)
        rendered = json.dumps(output, indent=2, ensure_ascii=True) + "\n"
        if args.out:
            with args.out.open("x", encoding="utf-8") as dest:
                dest.write(rendered)
        else:
            print(rendered, end="")
        return 0 if output["inventory_verified"] else 1
    except (OSError, ValueError):
        # File paths, adb diagnostics and JSON error text can contain private values.
        print("Audit failed: check tool/file availability, snapshot format and HomeMode declarations. "
              "Existing output files are never overwritten.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
