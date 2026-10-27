#!/usr/bin/env python3
"""Regression checks for source-backed, explicitly qualified performance images."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("vibe_performance", ROOT / "tools/render_performance.py")
renderer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(renderer)


class PerformanceAssetsTests(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(renderer.DATA.read_text())

    def capture_text(self, data):
        """Observe the text sent to the actual renderer without relying on OCR."""
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch.object(renderer, "text", wraps=renderer.text) as draw_text:
                renderer.render(data, Path(directory))
            return [call.args[2] for call in draw_text.call_args_list]

    def test_recorded_values_match_documented_snapshots(self):
        expected = {
            "original": (150, 594, 124),
            "vanilla": (219, 680, None),
            "optimized": (90, 782, 71),
        }
        actual = {s["id"]: (s["home_ms"], s["available_mib"], s["home_pss_mib"])
                  for s in self.data["states"]}
        self.assertEqual(actual, expected)
        source = (ROOT / self.data["source"]).read_text()
        for recorded in ["219 ms", "90 ms", "680 MiB", "782 MiB", "71 MiB", "150 ms / 594 MB / 124 MB"]:
            self.assertIn(recorded, source)

    def test_percentages_use_the_explicit_metric_and_baseline(self):
        expected = {
            "home_response": ("vanilla", "home_ms", 58.9041095890411, 59),
            "available_memory": ("vanilla", "available_mib", 15, 15),
            "home_process_memory": ("original", "home_pss_mib", 42.741935483871, 43),
        }
        self.assertEqual({c["id"] for c in self.data["claims"]}, set(expected))
        for claim in self.data["claims"]:
            baseline, metric, exact, rounded = expected[claim["id"]]
            with self.subTest(claim=claim["id"]):
                self.assertEqual(claim["baseline"], baseline)
                self.assertEqual(claim["comparison"], "optimized")
                self.assertEqual(claim["metric"], metric)
                self.assertAlmostEqual(renderer.claim_percent(self.data, claim), exact)
                self.assertEqual(claim["rounded_percent"], rounded)

    def test_unknown_preoptimization_process_memory_stays_unknown(self):
        states = {s["id"]: s for s in self.data["states"]}
        self.assertIsNone(states["vanilla"]["home_pss_mib"])
        pss_claim = next(c for c in self.data["claims"] if c["metric"] == "home_pss_mib")
        self.assertEqual(pss_claim["baseline"], "original")

    def test_nonpositive_baselines_are_rejected(self):
        for baseline in [0, -1]:
            with self.subTest(baseline=baseline):
                data = copy.deepcopy(self.data)
                data["states"][1]["home_ms"] = baseline
                with self.assertRaises(ValueError):
                    renderer.claim_percent(data, data["claims"][0])

    def test_missing_baseline_and_comparison_are_rejected(self):
        for index in [1, 2]:
            with self.subTest(state=index):
                data = copy.deepcopy(self.data)
                data["states"][index]["home_ms"] = None
                with self.assertRaises(ValueError):
                    renderer.claim_percent(data, data["claims"][0])

    def test_rendered_files_are_full_resolution_pngs(self):
        with tempfile.TemporaryDirectory() as directory:
            renderer.render(self.data, Path(directory))
            self.assertEqual({p.name for p in Path(directory).iterdir()}, {"comparison.png", "highlights.png"})
            for name in ["comparison.png", "highlights.png"]:
                with Image.open(Path(directory) / name) as image:
                    self.assertEqual(image.format, "PNG")
                    self.assertEqual(image.size, (2400, 1500))
                    self.assertEqual(image.mode, "RGB")
                    self.assertGreater(len(image.getcolors(image.width * image.height)), 100)

    def test_marketing_raw_values_track_changed_measurements(self):
        data = copy.deepcopy(self.data)
        data["states"][1]["home_ms"] = 180
        texts = self.capture_text(data)
        self.assertIn("180 → 90 ms", texts, "Marketing must read values from the data, not retain a stale 219.")
        self.assertNotIn("219 → 90 ms", texts)

    def test_marketing_percentages_track_changed_measurements(self):
        data = copy.deepcopy(self.data)
        data["states"][1]["home_ms"] = 180
        data["claims"][0]["rounded_percent"] = 50
        texts = self.capture_text(data)
        self.assertIn("50%", texts, "A valid updated measurement must update the promotional number too.")
        self.assertNotIn("59%", texts)

    def test_marketing_labels_state_each_distinct_baseline(self):
        texts = self.capture_text(self.data)
        self.assertEqual(texts.count("vs Vibe TV before optimization"), 2)
        self.assertIn("vs historical Original Google TV", texts)
        self.assertIn("Available memory is system headroom, not app memory usage. No cold-start or frame-rate gain is claimed.", texts)

    def test_images_disclose_measurement_limits(self):
        texts = self.capture_text(self.data)
        for disclosure in [
            "Historical snapshots. Different conditions. No controlled three-way benchmark.",
            "Different baselines are stated on each card. These are not controlled A/B results or universal speed claims.",
            "Reboot and cache state affect memory. Cold-launch and typical frame-time improvements are not established.",
        ]:
            self.assertIn(disclosure, texts)
        self.assertIn("not a controlled three-way benchmark", self.data["caveat"])


if __name__ == "__main__":
    unittest.main()
