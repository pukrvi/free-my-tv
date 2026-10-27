#!/usr/bin/env python3
"""Evidence, publication-gate and SVG contracts for public feature scenes."""
import base64
import copy
import importlib.util
import json
import re
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
SPEC = importlib.util.spec_from_file_location("vibe_feature_assets", ROOT / "tools/render_feature_assets.py")
renderer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(renderer)
SVG = {"s": "http://www.w3.org/2000/svg"}
HREF = "{http://www.w3.org/1999/xlink}href"


class FeatureAssetsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads((ROOT / "documentation/feature-assets/evidence.json").read_text())
        cls.temp = tempfile.TemporaryDirectory()
        cls.output = Path(cls.temp.name)
        (cls.output / "evidence.json").write_text(json.dumps(cls.evidence))
        with mock.patch.object(renderer, "OUT", cls.output):
            renderer.main()
        cls.scenes = {name: ET.parse(cls.output / (name + ".svg")).getroot()
                      for name in ["continue-watching", "privacy-and-ads", "performance"]}

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def strings(self, name):
        return [node.text or "" for node in self.scenes[name].findall(".//s:text", SVG)]

    def embedded(self, name):
        result = []
        for image in self.scenes[name].findall(".//s:image", SVG):
            uri = image.attrib[HREF]
            prefix, payload = uri.split(",", 1)
            self.assertIn(prefix, ["data:image/png;base64", "data:image/webp;base64"])
            result.append(base64.b64decode(payload, validate=True))
        return result

    def run_recommendations(self, evidence, git_return=0):
        context = tempfile.TemporaryDirectory()
        self.addCleanup(context.cleanup)
        output = Path(context.name)
        (output / "evidence.json").write_text(json.dumps(evidence))
        patches = mock.patch.multiple(renderer, OUT=output)
        patches.start()
        self.addCleanup(patches.stop)
        git = mock.patch.object(renderer.subprocess, "run", return_value=mock.Mock(returncode=git_return))
        process = git.start()
        self.addCleanup(git.stop)
        return output, process

    def landed(self):
        evidence = copy.deepcopy(self.evidence)
        evidence["recommendations"].update(
            status="landed", commit="a" * 40,
            screenshot="documentation/screenshots/home-playback.png",
            approved_copy=["Reviewed recommendation test copy."],
            limitations=["Routing depends on app support."],
        )
        return evidence

    def test_svg_and_png_exports_have_matching_canvas(self):
        for name, svg in self.scenes.items():
            with self.subTest(scene=name):
                self.assertEqual(svg.attrib["viewBox"], "0 0 1920 1080")
                self.assertEqual(svg.attrib["width"], "1920")
                self.assertEqual(svg.attrib["height"], "1080")
                with Image.open(self.output / (name + ".png")) as png:
                    self.assertEqual(png.format, "PNG")
                    self.assertEqual(png.size, (1920, 1080))
                    self.assertEqual(png.mode, "RGB")

    def test_svg_groups_have_unique_stable_animation_ids(self):
        expected = {
            "continue-watching": ["brand", "headline", "benefit-copy", "feature-labels", "device-evidence", "evidence-caption", "disclosure"],
            "privacy-and-ads": ["brand", "headline", "before-label", "before-evidence", "after-label", "after-evidence", "verified-scope", "disclosure"],
            "performance": ["brand", "headline", "metric-labels", "state-original", "state-vanilla", "state-optimized", "disclosure"],
        }
        for name, root in self.scenes.items():
            ids = [node.attrib["id"] for node in root.findall(".//s:g", SVG)]
            with self.subTest(scene=name):
                self.assertEqual(ids, expected[name])
                self.assertEqual(len(ids), len(set(ids)))

    def test_svg_has_no_javascript_event_handlers_or_remote_images(self):
        for name, root in self.scenes.items():
            with self.subTest(scene=name):
                self.assertEqual(root.findall(".//s:script", SVG), [])
                for node in root.iter():
                    self.assertFalse(any(key.lower().startswith("on") for key in node.attrib))
                    for key, value in node.attrib.items():
                        self.assertNotIn("javascript:", value.lower())
                        if key in [HREF, "href"]:
                            self.assertTrue(value.startswith("data:image/"))
                        self.assertNotIn("url(http", value.lower())

    def test_continue_screenshot_embeds_exact_approved_public_source(self):
        source = ROOT / self.evidence["continue_watching"]["screenshot"]
        self.assertEqual(self.embedded("continue-watching"), [source.read_bytes()])
        self.assertIn("ACTUAL TCL CAPTURE / ANDROID 12 / 27 OCT 2026", self.strings("continue-watching"))

    def test_privacy_images_embed_exact_sources_in_disclosed_order(self):
        evidence = self.evidence["privacy"]
        self.assertEqual(self.embedded("privacy-and-ads"), [
            (ROOT / evidence["before"]).read_bytes(),
            (ROOT / evidence["after"]).read_bytes(),
        ])

    def test_privacy_copy_keeps_upstream_unmatched_and_inapp_limits(self):
        strings = self.strings("privacy-and-ads")
        self.assertIn("Upstream illustration • not this TCL before a switch", strings)
        self.assertIn("UI comparison across sources, not a matched before/after tracker measurement. Tracking inside apps is not blocked.", strings)
        self.assertIn("TCL test: optional feedback, Play Movies and printing disabled. No claim of eliminating all TCL telemetry or in-app ads.", strings)
        self.assertNotIn("zero tracking", " ".join(strings).lower())

    def test_performance_is_vector_with_source_values_and_snapshot_disclosure(self):
        self.assertEqual(self.embedded("performance"), [])
        strings = self.strings("performance")
        data = json.loads((ROOT / "documentation/performance/measurements.json").read_text())
        for state in data["states"]:
            self.assertIn(str(state["home_ms"]) + " ms", strings)
            self.assertIn(str(state["available_mib"]) + " MiB", strings)
        self.assertIn("Historical snapshots, not a controlled three-way benchmark. Reboot and cache state affect memory.", strings)
        state_map = {s["id"]: s for s in data["states"]}
        for claim in data["claims"]:
            before = state_map[claim["baseline"]][claim["metric"]]
            after = state_map[claim["comparison"]][claim["metric"]]
            change = (after - before) / before * 100
            if claim["direction"] == "reduction":
                change *= -1
            self.assertEqual(round(change), claim["rounded_percent"])
        self.assertIsNone(state_map["vanilla"]["home_pss_mib"])

    def test_pending_recommendations_skip_all_public_assets(self):
        evidence = copy.deepcopy(self.evidence)
        evidence["recommendations"]["status"] = "pending"
        output, git = self.run_recommendations(evidence)
        renderer.recommendations()
        self.assertFalse((output / "recommendations.svg").exists())
        self.assertFalse((output / "recommendations.png").exists())
        git.assert_not_called()

    def test_pending_recommendations_remove_stale_exports_only(self):
        evidence = self.landed()
        output, git = self.run_recommendations(evidence)
        renderer.recommendations()
        self.assertTrue((output / "recommendations.svg").is_file())
        self.assertTrue((output / "recommendations.png").is_file())
        unrelated = output / "continue-watching.png"
        unrelated.write_bytes(b"unrelated feature export")
        evidence["recommendations"]["status"] = "pending"
        (output / "evidence.json").write_text(json.dumps(evidence))
        git.reset_mock()
        renderer.recommendations()
        self.assertFalse((output / "recommendations.svg").exists())
        self.assertFalse((output / "recommendations.png").exists())
        self.assertEqual(unrelated.read_bytes(), b"unrelated feature export")
        self.assertEqual(json.loads((output / "evidence.json").read_text()), evidence)
        git.assert_not_called()

    def test_landed_recommendations_require_nonempty_approved_copy(self):
        for value in [None, [], "Reviewed copy", [""], ["   "], [None], ["Valid", 12]]:
            with self.subTest(value=value):
                evidence = self.landed()
                evidence["recommendations"]["approved_copy"] = value
                output, git = self.run_recommendations(evidence)
                with self.assertRaisesRegex(ValueError, "approved_copy"):
                    renderer.recommendations()
                self.assertFalse((output / "recommendations.svg").exists())
                self.assertFalse((output / "recommendations.png").exists())
                git.assert_not_called()

    def test_landed_recommendations_require_nonempty_limitations(self):
        for value in [None, [], "Provider limitation", [""], ["\t"], [False], ["Valid", {}]]:
            with self.subTest(value=value):
                evidence = self.landed()
                evidence["recommendations"]["limitations"] = value
                output, git = self.run_recommendations(evidence)
                with self.assertRaisesRegex(ValueError, "limitations"):
                    renderer.recommendations()
                self.assertFalse((output / "recommendations.svg").exists())
                self.assertFalse((output / "recommendations.png").exists())
                git.assert_not_called()

    def test_landed_recommendations_require_commit(self):
        evidence = self.landed()
        evidence["recommendations"]["commit"] = None
        output, git = self.run_recommendations(evidence)
        with self.assertRaisesRegex(ValueError, "origin/main"):
            renderer.recommendations()
        self.assertFalse((output / "recommendations.svg").exists())
        git.assert_not_called()

    def test_unmerged_landed_commit_is_refused(self):
        evidence = self.landed()
        output, git = self.run_recommendations(evidence, git_return=1)
        with self.assertRaisesRegex(ValueError, "origin/main"):
            renderer.recommendations()
        git.assert_called_once_with(["git", "merge-base", "--is-ancestor", "a" * 40, "origin/main"], cwd=ROOT, capture_output=True)
        self.assertFalse((output / "recommendations.png").exists())

    def test_landed_recommendations_require_actual_source_capture(self):
        for screenshot in [None, "documentation/screenshots/nonexistent-reviewed-capture.png"]:
            with self.subTest(screenshot=screenshot):
                evidence = self.landed()
                evidence["recommendations"]["screenshot"] = screenshot
                output, _ = self.run_recommendations(evidence)
                with self.assertRaisesRegex(ValueError, "screenshot"):
                    renderer.recommendations()
                self.assertFalse((output / "recommendations.svg").exists())

    def test_landed_recommendations_embed_capture_and_commit_caption(self):
        evidence = self.landed()
        output, _ = self.run_recommendations(evidence)
        renderer.recommendations()
        root = ET.parse(output / "recommendations.svg").getroot()
        strings = [node.text or "" for node in root.findall(".//s:text", SVG)]
        self.assertIn("ACTUAL TCL CAPTURE / LANDED BUILD aaaaaaa", strings)
        self.assertIn("Reviewed recommendation test copy.", strings)
        self.assertIn("Routing depends on app support.", strings)
        image = root.find(".//s:image", SVG)
        raw = base64.b64decode(image.attrib[HREF].split(",", 1)[1], validate=True)
        self.assertEqual(raw, (ROOT / evidence["recommendations"]["screenshot"]).read_bytes())


class WikiSingleSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("vibe_wiki", ROOT / "tools/sync_wiki.py")
        cls.wiki = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.wiki)

    def test_home_is_rewritten_readme_plus_generated_source_note(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            self.wiki.generate(output)
            source = "README.md"
            body = self.wiki.rewrite_links((ROOT / source).read_text(), source)
            note = (f"\n\nSource: [{source}]({self.wiki.BASE}/blob/main/{source}). "
                    "This page is generated; edit the source guide.\n")
            self.assertEqual((output / "Home.md").read_text(), body.rstrip() + note)

    def test_generating_wiki_leaves_authoritative_source_bytes_unchanged(self):
        before = {source: (ROOT / source).read_bytes() for source in self.wiki.SOURCES.values()}
        with tempfile.TemporaryDirectory() as directory:
            self.wiki.generate(Path(directory))
        after = {source: (ROOT / source).read_bytes() for source in self.wiki.SOURCES.values()}
        self.assertEqual(after, before)

    def test_images_reference_repo_assets_without_wiki_copies(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            self.wiki.generate(output)
            images = []
            for page in output.glob("*.md"):
                images.extend(re.findall(r'!\[[^\]]*\]\(([^)]+)\)', page.read_text()))
            self.assertGreater(len(images), 0, "The generated guides should retain their source images.")
            for image in images:
                with self.subTest(image=image):
                    self.assertTrue(image.startswith("https://raw.githubusercontent.com/pukrvi/vibe-tv/main/"))
                    self.assertNotIn("/wiki/", image)
            self.assertEqual(len(list(output.iterdir())), len(self.wiki.SOURCES) + 2)
            self.assertTrue(all(path.is_file() and path.suffix == ".md" for path in output.iterdir()))

    def test_every_page_uses_existing_authoritative_guide_and_home_has_no_second_source(self):
        self.assertEqual(self.wiki.SOURCES["Home"], "README.md")
        self.assertEqual(len(set(self.wiki.SOURCES.values())), len(self.wiki.SOURCES))
        for page, source in self.wiki.SOURCES.items():
            with self.subTest(page=page):
                self.assertTrue((ROOT / source).is_file())
                self.assertFalse(source.startswith("documentation/wiki/"))
        self.assertFalse((ROOT / "documentation/wiki/Home.md").exists())


if __name__ == "__main__":
    unittest.main()
