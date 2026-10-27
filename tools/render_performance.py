#!/usr/bin/env python3
"""Render exact, source-backed Vibe TV performance figures. Requires Pillow."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "documentation/performance/measurements.json"
OUT = ROOT / "documentation/performance"
BG, PANEL, LINE, WHITE, MUTED, ACCENT = "#101214", "#1C1F22", "#454B50", "#F5F5F4", "#ADB3B8", "#E8C97D"


def claim_percent(data, claim):
    states = {s["id"]: s for s in data["states"]}
    before = states[claim["baseline"]][claim["metric"]]
    after = states[claim["comparison"]][claim["metric"]]
    if before is None or after is None or before <= 0:
        raise ValueError("A percentage requires measured values and a positive baseline")
    delta = (after - before) / before * 100
    return -delta if claim["direction"] == "reduction" else delta


def font(size, bold=False):
    f = ImageFont.truetype(str(ROOT / "docs/assets/fonts/InterVariable.ttf"), size)
    f.set_variation_by_axes([700 if bold else 400])
    return f


def text(draw, xy, content, size=30, fill=WHITE, bold=False, anchor=None):
    draw.text(xy, content, font=font(size, bold), fill=fill, anchor=anchor)


def surface(size):
    im = Image.new("RGB", size, BG)
    return im, ImageDraw.Draw(im)


def badge(draw, x, y, value):
    draw.rounded_rectangle((x, y, x + 238, y + 56), radius=28, fill=PANEL, outline=LINE, width=2)
    text(draw, (x + 119, y + 28), value, 21, anchor="mm")


def render(data, out=OUT):
    out.mkdir(parents=True, exist_ok=True)
    states = data["states"]
    im, d = surface((2400, 1500))
    text(d, (120, 70), "VIBE TV", 34, bold=True)
    text(d, (120, 139), "Three states. One TV.", 86, bold=True)
    text(d, (124, 259), "Home response and available memory, before and after optimization.", 31, MUTED)
    badge(d, 2038, 83, "TCL / ANDROID 12")
    # One comparison figure: each metric has its own explicit scale and units.
    x_time, x_mem, colw = 820, 1570, 580
    text(d, (x_time, 364), "HOME RESPONSE", 24, MUTED, True)
    text(d, (x_time, 407), "Milliseconds · lower is better", 26)
    text(d, (x_mem, 364), "AVAILABLE MEMORY", 24, MUTED, True)
    text(d, (x_mem, 407), "MiB · higher is better", 26)
    for col, maximum, unit in [(x_time, 250, "ms"), (x_mem, 900, "MiB")]:
        for n in range(4):
            x = col + colw * n / 3
            d.line((x, 484, x, 1090), fill="#2B3034", width=2)
            text(d, (x, 1117), f"{maximum*n/3:.0f}", 22, MUTED, anchor="mt")
    for i, state in enumerate(states):
        y = 498 + i * 201
        active = state["id"] == "optimized"
        d.rounded_rectangle((110, y - 13, 2290, y + 165), radius=27, outline=WHITE if active else LINE, width=3 if active else 1)
        text(d, (149, y + 19), ["01", "02", "03"][i], 23, ACCENT if active else MUTED)
        text(d, (210, y + 14), ["Original Google TV", "Vibe TV · before", "Vibe TV · optimized"][i], 36, bold=True)
        text(d, (210, y + 76), state["context"], 23, MUTED)
        for x, metric, maximum, unit in [(x_time, "home_ms", 250, "ms"), (x_mem, "available_mib", 900, "MiB")]:
            width = colw * state[metric] / maximum
            d.rounded_rectangle((x, y + 37, x + width, y + 99), radius=13, fill=ACCENT if active else "#69737B")
            text(d, (x + width + 20, y + 67), f'{state[metric]} {unit}', 28, bold=active, anchor="lm")
    text(d, (120, 1223), "Historical snapshots. Different conditions. No controlled three-way benchmark.", 30, bold=True)
    text(d, (120, 1280), "Original: retained baseline. Before: owner report. Optimized: after one restart; reboot/cache state affect RAM.", 25, MUTED)
    text(d, (120, 1330), "Available memory is system headroom, not app memory usage. No cold-start or frame-rate gain is claimed.", 25, MUTED)
    text(d, (120, 1410), "27 OCT 2026  /  SOURCE: TCL_G10_OPTIMIZATION.md", 22, MUTED)
    text(d, (2280, 1410), "NO ADS. NO BLOAT. ONLY VIBES.", 22, WHITE, anchor="ra")
    im.save(out / "comparison.png", optimize=True)

    im, d = surface((2400, 1500))
    text(d, (120, 75), "VIBE TV", 34, bold=True)
    text(d, (120, 158), "Less waiting. More room.", 94, bold=True)
    text(d, (126, 300), "Measured snapshots from our TCL Android 12 test TV.", 33, MUTED)
    state_by_id = {state["id"]: state for state in states}
    metric_labels = {
        "home_ms": ("Home response time", "ms"),
        "available_mib": ("available memory", "MiB"),
        "home_pss_mib": ("Home process memory", "MiB"),
    }
    cards = []
    for claim in data["claims"]:
        before = state_by_id[claim["baseline"]]
        after = state_by_id[claim["comparison"]]
        metric = claim["metric"]
        label, unit = metric_labels[metric]
        title = ("Lower " if claim["direction"] == "reduction" else "More ") + label
        baseline = "vs " + ("historical " if before["id"] == "original" else "") + before["label"]
        values = f"{before[metric]:g} → {after[metric]:g} {unit}"
        cards.append((f"{round(claim_percent(data, claim))}%", title, values, baseline))
    for i, (num, title, values, baseline) in enumerate(cards):
        x = 120 + i * 730
        d.rounded_rectangle((x, 450, x + 700, 1030), radius=36, fill=PANEL, outline=WHITE if i == 0 else LINE, width=3 if i == 0 else 2)
        text(d, (x + 42, 491), f"0{i+1} / SNAPSHOT CHANGE", 22, MUTED)
        text(d, (x + 42, 560), num, 174, ACCENT if i == 0 else WHITE, True)
        text(d, (x + 42, 790), title, 31, bold=True)
        text(d, (x + 42, 855), values, 39)
        text(d, (x + 42, 950), baseline, 25, MUTED)
    text(d, (120, 1127), "No Ads, No Bloat, Only Vibes.", 48, bold=True)
    text(d, (120, 1230), "Different baselines are stated on each card. These are not controlled A/B results or universal speed claims.", 26, MUTED)
    text(d, (120, 1280), "One TV · original retained baseline · earlier owner reading · optimized reading after restart", 26, MUTED)
    text(d, (120, 1330), "Reboot and cache state affect memory. Cold-launch and typical frame-time improvements are not established.", 26, MUTED)
    text(d, (120, 1410), "27 OCT 2026  /  SOURCE: TCL_G10_OPTIMIZATION.md", 22, MUTED)
    text(d, (2280, 1410), "github.com/pukrvi/vibe-tv", 24, anchor="ra")
    im.save(out / "highlights.png", optimize=True)


def main():
    data = json.loads(DATA.read_text())
    for claim in data["claims"]:
        if round(claim_percent(data, claim)) != claim["rounded_percent"]:
            raise ValueError(f"Invalid rounded claim: {claim['id']}")
    render(data)
    print("Rendered two 2400×1500 performance images from recorded measurements.")


if __name__ == "__main__":
    main()
