#!/usr/bin/env python3
"""Authorized active TV test: Settings -> Home timing and fixed D-pad navigation.

No preferences or playback are changed. Navigation/focus and graphics counters
are changed; optional cold launches interrupt the app's processes temporarily.
Reports omit the device address. Run only when the owner has authorized testing.
"""
import argparse
import json
from pathlib import Path
import re
import statistics
import subprocess
import time


KEYS = (22, 22, 22, 21, 21, 21, 20, 22, 21, 20, 22, 21, 19, 19)
HOME = "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--adb", required=True)
    p.add_argument("--serial", required=True)
    p.add_argument("--out", required=True, type=Path)
    p.add_argument("--trace", type=Path)
    p.add_argument("--cold", action="store_true")
    p.add_argument("--quick", action="store_true", help="Three warm returns and one navigation run for final restart checks")
    p.add_argument("--explicit-home", action="store_true", help="Measure Vibe directly when recovery leaves another launcher as Home")
    args = p.parse_args()
    if args.out.exists() or (args.trace and args.trace.exists()):
        p.error("Output already exists")

    def shell(command, timeout=30):
        r = subprocess.run([args.adb, "-s", args.serial, "shell", command],
                           capture_output=True, timeout=timeout, check=True)
        return r.stdout.decode("utf-8", "replace")

    def total(output):
        m = re.search(r"^TotalTime:\s*(\d+)", output, re.M)
        if not m:
            raise RuntimeError("No launch timing returned")
        return int(m[1])

    home = HOME + (" -n io.freemytv/.HomeActivity" if args.explicit_home else "")
    result = {"protocol": "quick" if args.quick else "full", "explicit_home": args.explicit_home,
              "warm_ms": [], "cold_ms": [], "navigation": []}
    shell(home)
    time.sleep(3)
    for i in range(3 if args.quick else 7):
        shell("am start -W -n io.freemytv/.SettingsActivity")
        time.sleep(0.5)
        result["warm_ms"].append(total(shell(home)))
        time.sleep(0.6)
    print("Warm Home median:", statistics.median(result["warm_ms"]), "ms", flush=True)
    if args.cold:
        for i in range(3):
            # One atomic launcher command prevents Android auto-starting Home during a separate stop/start gap.
            value = total(shell("am start -S -W -n io.freemytv/.HomeActivity"))
            if value <= 0:
                raise RuntimeError("Cold launch was not measured; zero-duration reused activities are invalid")
            result["cold_ms"].append(value)
            time.sleep(4)
        print("Cold Home median:", statistics.median(result["cold_ms"]), "ms", flush=True)
    tracing = False
    try:
        if args.trace:
            shell("atrace --async_start -b 16384 -a io.freemytv gfx view sched freq am wm dalvik")
            tracing = True
        for i in range(1 if args.quick else 3):
            shell(home)
            time.sleep(2)
            shell("dumpsys gfxinfo io.freemytv:home reset")
            for key in KEYS * 2:
                shell("input keyevent " + str(key))
                time.sleep(0.35)
            time.sleep(1)
            output = shell("dumpsys gfxinfo io.freemytv:home")
            def metric(pattern):
                m = re.search(pattern, output, re.M)
                return int(m[1]) if m else None
            row = {"frames": metric(r"Total frames rendered:\s*(\d+)"),
                   "janky": metric(r"Janky frames:\s*(\d+)"),
                   "p50_ms": metric(r"50th percentile:\s*(\d+)ms"),
                   "p90_ms": metric(r"90th percentile:\s*(\d+)ms"),
                   "slow_ui": metric(r"Number Slow UI thread:\s*(\d+)"),
                   "slow_draw": metric(r"Number Slow issue draw commands:\s*(\d+)")}
            if not row["frames"]:
                raise RuntimeError("No frames measured")
            row["jank_percent"] = round(100 * row["janky"] / row["frames"], 2)
            result["navigation"].append(row)
            print("Navigation", i + 1, json.dumps(row), flush=True)
    finally:
        if tracing:
            args.trace.write_text(shell("atrace --async_stop", timeout=60))
        shell(home)
    output = shell("cat /proc/meminfo")
    m = re.search(r"MemAvailable:\s*(\d+)", output)
    result["available_kib"] = int(m[1]) if m else None
    output = shell("dumpsys meminfo io.freemytv:home")
    m = re.search(r"TOTAL PSS:\s*(\d+)", output)
    result["home_pss_kib"] = int(m[1]) if m else None
    result["warm_median_ms"] = statistics.median(result["warm_ms"])
    result["navigation_jank_median_percent"] = statistics.median(r["jank_percent"] for r in result["navigation"])
    result["navigation_p50_median_ms"] = statistics.median(r["p50_ms"] for r in result["navigation"])
    result["target_met"] = (result["warm_median_ms"] <= 150 and result["navigation_jank_median_percent"] <= 10
                            and result["navigation_p50_median_ms"] <= 16)
    args.out.write_text(json.dumps(result, indent=2) + "\n")
    print("Targets: warm <=150ms, navigation jank <=10%, median frame <=16ms:", "PASS" if result["target_met"] else "FAIL", flush=True)


if __name__ == "__main__":
    main()
