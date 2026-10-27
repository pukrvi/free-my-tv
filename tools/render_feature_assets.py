#!/usr/bin/env python3
"""Build editable, grouped SVG scenes and matching PNGs from public evidence.

Vector typography, frames, labels and diagrams remain editable. Screenshots remain
unaltered raster image layers; they are never claimed to be vector screenshots.
"""
import base64
import json
import subprocess
from html import escape
from pathlib import Path
from PIL import Image, ImageDraw
from render_performance import font

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "documentation/feature-assets"
BG, PANEL, LINE, WHITE, MUTED, ACCENT = "#101214", "#1C1F22", "#454B50", "#F5F5F4", "#ADB3B8", "#E8C97D"


class Scene:
    def __init__(self, name, title):
        self.name = name
        self.image = Image.new("RGB", (1920, 1080), BG)
        self.draw = ImageDraw.Draw(self.image)
        self.svg = [f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 1920 1080" width="1920" height="1080" role="img"><title>{escape(title)}</title><desc>Evidence-led Vibe TV feature scene. Editable vector text and shapes with embedded raster evidence.</desc><rect width="1920" height="1080" fill="{BG}"/>']
        self.group = None

    def begin(self, name):
        if self.group:
            self.svg.append('</g>')
        self.svg.append(f'<g id="{escape(name)}">')
        self.group = name

    def text(self, x, y, content, size=28, fill=WHITE, bold=False):
        self.draw.text((x, y), content, font=font(size, bold), fill=fill, anchor="ls")
        self.svg.append(f'<text x="{x}" y="{y}" fill="{fill}" font-family="Inter,Arial,sans-serif" font-size="{size}" font-weight="{700 if bold else 400}">{escape(content)}</text>')

    def rect(self, x, y, w, h, fill=PANEL, stroke=LINE, radius=24):
        self.draw.rounded_rectangle((x, y, x+w, y+h), radius=radius, fill=fill, outline=stroke, width=2)
        self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')

    def picture(self, source, x, y, w, h):
        source = ROOT / source
        data = source.read_bytes()
        mime = "image/webp" if source.suffix == ".webp" else "image/png"
        with Image.open(source) as im:
            # Resize only; never invent/replace any screenshot content.
            self.image.paste(im.convert("RGB").resize((w, h), Image.Resampling.LANCZOS), (x, y))
        uri = f'data:{mime};base64,' + base64.b64encode(data).decode()
        self.svg.append(f'<image x="{x}" y="{y}" width="{w}" height="{h}" preserveAspectRatio="none" xlink:href="{uri}"/>')

    def save(self):
        if self.group:
            self.svg.append('</g>')
        self.svg.append('</svg>')
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / f'{self.name}.svg').write_text('\n'.join(self.svg)+'\n')
        self.image.save(OUT / f'{self.name}.png', optimize=True)


def base(name, title, kicker):
    scene = Scene(name, title)
    scene.begin("brand")
    scene.text(80, 80, "VIBE TV", 30, bold=True)
    scene.text(80, 128, kicker, 20, MUTED)
    return scene


def continue_watching():
    s = base("continue-watching", "Continue Watching: titles, artwork and progress on the home screen", "02 / CONTINUE WATCHING")
    s.begin("headline")
    s.text(80, 272, "Your next play.", 76, bold=True)
    s.text(80, 366, "Already here.", 76, bold=True)
    s.begin("benefit-copy")
    for i, line in enumerate(["Recent titles, real artwork and progress,", "together on your home screen.", "", "Opt in. Pick a card with your remote.", "Keep your streaming apps in control."]):
        s.text(84, 457+i*38, line, 27, MUTED)
    s.begin("feature-labels")
    for i, label in enumerate(["TITLE", "ARTWORK", "PROGRESS"]):
        s.rect(84+i*188, 718, 170, 58, stroke=WHITE if i==2 else LINE)
        s.text(104+i*188, 756, label, 20, ACCENT if i==2 else WHITE)
    s.begin("device-evidence")
    s.rect(704, 226, 1138, 646, stroke=WHITE, radius=12)
    s.picture("documentation/screenshots/home-playback.png", 710, 232, 1126, 634)
    s.begin("evidence-caption")
    s.text(710, 920, "ACTUAL TCL CAPTURE / ANDROID 12 / 27 OCT 2026", 21, MUTED)
    s.begin("disclosure")
    s.text(80, 1008, "Artwork and progress shown for YouTube / Stremio. Reopening and saved-position support depend on the app and build.", 23, MUTED)
    s.text(80, 1045, "No Ads, No Bloat, Only Vibes.  •  Evidence and provider limits: PLAYBACK_STATUS.md", 20, MUTED)
    s.save()


def privacy():
    s = base("privacy-and-ads", "A quieter launcher, with verified optional cleanup and clear privacy limits", "04 / A QUIETER HOME, MORE CONTROL")
    s.begin("headline")
    s.text(80, 220, "Your home. Without the sponsored rows.", 65, bold=True)
    s.begin("before-label")
    s.text(80, 290, "STOCK-HOME EXAMPLE", 24, MUTED, True)
    s.text(80, 328, "Upstream illustration • not this TCL before a switch", 22, MUTED)
    s.begin("before-evidence")
    s.rect(74, 362, 864, 491, stroke=LINE, radius=12)
    s.picture("docs/assets/before.webp", 80, 368, 852, 479)
    s.begin("after-label")
    s.text(992, 290, "VIBE TV", 24, ACCENT, True)
    s.text(992, 328, "Actual TCL capture • apps and playback, without sponsored rows", 22, MUTED)
    s.begin("after-evidence")
    s.rect(986, 362, 864, 491, stroke=WHITE, radius=12)
    s.picture("documentation/screenshots/home-playback.png", 992, 368, 852, 479)
    s.begin("verified-scope")
    s.text(80, 915, "NO SPONSORED LAUNCHER ROWS", 25, bold=True)
    s.text(728, 915, "SELECTED CLEANUP IS REVERSIBLE", 25, bold=True)
    s.text(1410, 915, "RECOVERY RETAINED", 25, bold=True)
    s.begin("disclosure")
    s.text(80, 986, "UI comparison across sources, not a matched before/after tracker measurement. Tracking inside apps is not blocked.", 23, MUTED)
    s.text(80, 1026, "TCL test: optional feedback, Play Movies and printing disabled. No claim of eliminating all TCL telemetry or in-app ads.", 23, MUTED)
    s.save()


def performance():
    data = json.loads((ROOT / "documentation/performance/measurements.json").read_text())
    s = base("performance", "Three measured performance states with explicit units and snapshot caveats", "03 / MEASURED ON OUR TCL")
    s.begin("headline")
    s.text(80, 235, "Less waiting. More room.", 78, bold=True)
    s.begin("metric-labels")
    s.text(660, 338, "HOME RESPONSE / ms / lower is better", 23, MUTED)
    s.text(1260, 338, "AVAILABLE MEMORY / MiB / higher is better", 23, MUTED)
    for i, state in enumerate(data["states"]):
        y = 405+i*165
        active = state["id"] == "optimized"
        s.begin("state-"+state["id"])
        s.rect(72, y-15, 1776, 140, fill=BG, stroke=WHITE if active else LINE)
        s.text(100, y+35, ["Original Google TV", "Vibe TV / before", "Vibe TV / optimized"][i], 34, bold=True)
        s.text(100, y+82, state["context"], 23, MUTED)
        for x, metric, maximum, unit in [(660,"home_ms",250,"ms"),(1260,"available_mib",900,"MiB")]:
            s.rect(x,y+22,430*state[metric]/maximum,48,fill=ACCENT if active else "#69737B",stroke=ACCENT if active else "#69737B",radius=10)
            s.text(x+430*state[metric]/maximum+18,y+57,f'{state[metric]} {unit}',27,bold=active)
    s.begin("disclosure")
    s.text(80, 974, "Historical snapshots, not a controlled three-way benchmark. Reboot and cache state affect memory.", 25, MUTED)
    s.text(80, 1024, "Original: retained reading. Before: owner report. Optimized: after restart. Source: TCL_G10_OPTIMIZATION.md", 23, MUTED)
    s.save()


def recommendations():
    evidence = json.loads((OUT / "evidence.json").read_text())["recommendations"]
    if evidence["status"] != "landed":
        # A downgraded status withdraws earlier public exports, not just new renders.
        for extension in ("svg", "png"):
            (OUT / f"recommendations.{extension}").unlink(missing_ok=True)
        print("Recommendation visual held: waiting for landed implementation and reviewed device capture.")
        return
    for field in ("approved_copy", "limitations"):
        lines = evidence.get(field)
        if not isinstance(lines, list) or not lines or not all(
            isinstance(line, str) and line.strip() for line in lines
        ):
            raise ValueError(f"Recommendation {field} must be a nonempty list of nonempty strings")
    commit = evidence["commit"]
    if not commit or subprocess.run(["git", "merge-base", "--is-ancestor", commit, "origin/main"], cwd=ROOT, capture_output=True).returncode:
        raise ValueError("Recommendation feature must be present on fetched origin/main before publication")
    source = evidence["screenshot"]
    if not source or not (ROOT/source).is_file():
        raise ValueError("A reviewed recommendation screenshot is required")
    s = base("recommendations", "Vibe TV recommendations from the reviewed landed build", "01 / RECOMMENDATIONS")
    s.begin("headline")
    s.text(80, 260, "Find your", 80, bold=True)
    s.text(80, 358, "next favorite.", 80, bold=True)
    s.begin("benefit-copy")
    for i,line in enumerate(evidence["approved_copy"]):
        s.text(84, 460+i*42, line, 27, MUTED)
    s.begin("device-evidence")
    s.rect(704,226,1138,646,stroke=WHITE,radius=12)
    s.picture(source,710,232,1126,634)
    s.begin("evidence-caption")
    s.text(710,920,"ACTUAL TCL CAPTURE / LANDED BUILD "+commit[:7],21,MUTED)
    s.begin("disclosure")
    for i,line in enumerate(evidence["limitations"]):
        s.text(80,990+i*40,line,23,MUTED)
    s.save()


def addons():
    s = base("catalog-addons", "Two Stremio catalog adapters in development", "DRAFT / STREMIO CATALOG ADD-ONS")
    s.begin("headline")
    s.text(80, 232, "Two catalog sources.", 78, bold=True)
    s.text(80, 330, "More ways to discover.", 78, bold=True)
    s.begin("anime-kitsu")
    s.rect(80, 430, 840, 185, stroke=WHITE)
    s.text(120, 500, "Anime Kitsu", 43, bold=True)
    s.text(120, 550, "Anime metadata, airing order and season links", 27, MUTED)
    s.begin("streaming-catalogs")
    s.rect(1000, 430, 840, 185, stroke=WHITE)
    s.text(1040, 500, "Streaming Catalogs", 43, bold=True)
    s.text(1040, 550, "Movie and show catalog metadata", 27, MUTED)
    s.begin("adapter-scope")
    s.rect(80, 680, 1760, 165)
    s.text(120, 750, "CATALOG METADATA / TWO DRAFT ADAPTERS", 30, ACCENT, True)
    s.text(120, 800, "Other catalogs need contributor code and tests. There is no arbitrary add-on installer.", 27, MUTED)
    s.begin("disclosure")
    s.text(80, 974, "In development. The expanded recommendation implementation and device validation are pending.", 25, MUTED)
    s.text(80, 1024, "The draft does not execute streams, subtitles or player plug-ins. Source and limits: documentation/ADDONS.md", 23, MUTED)
    s.save()


def main():
    continue_watching()
    privacy()
    performance()
    addons()
    recommendations()
    print("Rendered available grouped SVG masters and matching 1920×1080 PNGs.")


if __name__ == "__main__":
    main()
