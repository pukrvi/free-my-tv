#!/usr/bin/env python3
"""Build the GitHub wiki from the repository's authoritative guides.

Generated output is disposable. This command never runs Git or publishes.
"""
import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = "https://github.com/pukrvi/vibe-tv"
SOURCES = {
    "Home": "documentation/INDEX.md",
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
    "Add-ons": "documentation/ADDONS.md",
    "Feature-Stories": "documentation/FEATURE_STORIES.md",
    "Asset-Guide": "documentation/feature-assets/ASSET_GUIDE.md",
}


def rewrite_links(content, source):
    """Resolve repo links without changing claims, commands or image content."""
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
        elif mark:
            url = "https://raw.githubusercontent.com/pukrvi/vibe-tv/main/" + relative
        else:
            url = BASE + "/blob/main/" + relative + suffix
        return f"{mark}[{label}]({url})"

    return re.sub(r'(!?)\[([^\]]*)\]\(([^)]+)\)', replacement, content)


def generate(destination):
    destination.mkdir(parents=True, exist_ok=True)
    for page, source in SOURCES.items():
        content = rewrite_links((ROOT / source).read_text(), source)
        note = f"\n\nSource: [{source}]({BASE}/blob/main/{source}). This page is generated; edit the source guide.\n"
        (destination / (page + ".md")).write_text(content.rstrip() + note)
    labels = {"Getting-Started": "Install", "Hardware-and-Apps": "Hardware & apps", "Feature-Stories": "Feature guides", "Asset-Guide": "Assets & video"}
    sidebar = "# Vibe TV\n\n" + "\n".join(f"- [{labels.get(page,page)}]({BASE}/wiki/{page})" for page in SOURCES) + "\n"
    (destination / "_Sidebar.md").write_text(sidebar)
    (destination / "_Footer.md").write_text(f"Vibe TV · [Source]({BASE}) · Built on [Free My TV](https://github.com/simbasjungles/free-my-tv) · [MIT license]({BASE}/blob/main/LICENSE)\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=ROOT / ".wiki-build")
    args = parser.parse_args()
    generate(args.destination)
    print(f"Built {len(SOURCES)+2} wiki files in {args.destination}; source guides remain unchanged.")


if __name__ == "__main__":
    main()
