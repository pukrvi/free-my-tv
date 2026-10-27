#!/usr/bin/env python3
"""Build wiki pages from reviewed repository docs; optionally copy to a wiki checkout.

No network or git writes. Publish separately from the destination checkout.
"""
import argparse
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = "https://github.com/pukrvi/vibe-tv"
SOURCES = {
    "Getting-Started": "INSTALLATION.md",
    "Features": "FEATURES.md",
    "Hardware-and-Apps": "COMPATIBILITY.md",
    "Performance": "documentation/TCL_G10_OPTIMIZATION.md",
    "Contributing": "CONTRIBUTING.md",
    "Privacy": "PRIVACY.md",
    "Security": "SECURITY.md",
    "Design": "DESIGN.md",
    "Screenshots": "documentation/SCREENSHOTS.md",
    "Credits": "CREDITS.md",
}


def rewrite_links(content, source):
    """Map local docs to wiki pages and all other repo files to public URLs."""
    pages = {str((ROOT / path).resolve()): page for page, path in SOURCES.items()}

    def replacement(match):
        mark, label, target = match.groups()
        if target.startswith(("https://", "http://", "#", "mailto:")):
            return match.group(0)
        path, sep, anchor = target.partition("#")
        resolved = (ROOT / source).parent.joinpath(path).resolve()
        try:
            relative = resolved.relative_to(ROOT).as_posix()
        except ValueError:
            raise ValueError(f"Link outside repository: {target}")
        if not resolved.exists():
            raise ValueError(f"Missing wiki source target: {source}: {target}")
        suffix = sep + anchor if sep else ""
        if str(resolved) in pages and not mark:
            url = BASE + "/wiki/" + pages[str(resolved)] + suffix
        elif relative in {"documentation/performance/comparison.png", "documentation/performance/highlights.png"}:
            url = "https://raw.githubusercontent.com/wiki/pukrvi/vibe-tv/images/" + resolved.name
        else:
            url = BASE + ("/raw/refs/heads/main/" if mark else "/blob/main/") + relative + suffix
        return f"{mark}[{label}]({url})"

    return re.sub(r'(!?)\[([^\]]*)\]\(([^)]+)\)', replacement, content)


def generate(destination):
    destination.mkdir(parents=True, exist_ok=True)
    for page, source in SOURCES.items():
        content = rewrite_links((ROOT / source).read_text(), source)
        note = f"\n\n---\nSource: [{source}]({BASE}/blob/main/{source}). Edit the repository source, then run `python3 tools/sync_wiki.py`.\n"
        (destination / (page + ".md")).write_text(content.rstrip() + note)
    (destination / "Home.md").write_text(f'''# Vibe TV

**No Ads, No Bloat, Only Vibes.**

A remote-first Android TV home, built on Free My TV. Start with the [installation guide]({BASE}/wiki/Getting-Started), check [your hardware and apps]({BASE}/wiki/Hardware-and-Apps), or [contribute]({BASE}/wiki/Contributing).

## Explore the project

- [Getting started]({BASE}/wiki/Getting-Started): build, install, configure, recover.
- [Features and current status]({BASE}/wiki/Features).
- [Hardware and streaming-app results]({BASE}/wiki/Hardware-and-Apps).
- [Performance: original, before optimization, optimized]({BASE}/wiki/Performance).
- [Actual TV screenshots]({BASE}/wiki/Screenshots).
- [Design and remote navigation]({BASE}/wiki/Design).
- [Contribution guide]({BASE}/wiki/Contributing).
- [Privacy]({BASE}/wiki/Privacy) and [security reporting]({BASE}/wiki/Security).
- [Credits and upstream provenance]({BASE}/wiki/Credits).

## Measured snapshots, with their limits

![Three performance states](https://raw.githubusercontent.com/wiki/pukrvi/vibe-tv/images/comparison.png)

Historical snapshots, not a controlled three-way benchmark. See [Performance]({BASE}/wiki/Performance) for the raw values, source and controlled warm-return results.

This wiki is published from versioned sources under [`documentation/wiki`]({BASE}/tree/main/documentation/wiki). A repository PR alone does not update GitHub's separate wiki Git repository.
''')
    (destination / "_Sidebar.md").write_text("## Vibe TV\n\n" + "\n".join(f"- [{label}]({BASE}/wiki/{page})" for label, page in [("Home", "Home"), ("Install", "Getting-Started"), ("Features", "Features"), ("Hardware & apps", "Hardware-and-Apps"), ("Performance", "Performance"), ("Screenshots", "Screenshots"), ("Design", "Design"), ("Contribute", "Contributing"), ("Privacy", "Privacy"), ("Security", "Security"), ("Credits", "Credits")]) + "\n")
    (destination / "_Footer.md").write_text(f"Vibe TV · [Source]({BASE}) · Built on [Free My TV](https://github.com/simbasjungles/free-my-tv) · [MIT license]({BASE}/blob/main/LICENSE)\n")
    images = destination / "images"
    images.mkdir(exist_ok=True)
    for name in ["comparison.png", "highlights.png"]:
        shutil.copyfile(ROOT / "documentation/performance" / name, images / name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=ROOT / "documentation/wiki")
    args = parser.parse_args()
    generate(args.destination)
    print(f"Generated 13 wiki pages/navigation files and 2 images in {args.destination}")


if __name__ == "__main__":
    main()
