"""Offline contract checks: python3 -m unittest discover -s tools -p 'test_tv_audit.py'."""
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

import tv_audit as audit


class AuditTests(unittest.TestCase):
    def setUp(self):
        source = Path(__file__).resolve().parents[1] / "app/src/io/freemytv/HomeMode.java"
        self.rules = audit.policy(source.read_text())
        self.sample = {
            "packages": "package:tv.samba.ssm.ui\npackage:com.tcl.ttvs\n",
            "disabled": "package:tv.samba.ssm.ui\n",
            "model": "Smart TV Pro\n", "sdk": "31\n",
            "adb_secure": "1\n",
            "memory": "MemTotal: 2500000 kB\nMemAvailable: 800000 kB\nSwapFree: 200000 kB\n",
            "home_memory": "  TOTAL PSS: 123,456 TOTAL RSS: 200000\n",
            "frames": "Total frames rendered: 1000\nJanky frames: 20 (2.00%)\n",
        }

    def test_existing_blocking_and_protected_dependency(self):
        result = audit.report(self.sample, self.rules)
        rows = {r["package"]: r for r in result["packages"]}
        self.assertEqual((rows["tv.samba.ssm.ui"]["state"], rows["tv.samba.ssm.ui"]["policy"]),
                         ("disabled", "already targeted"))
        self.assertEqual((rows["com.tcl.ttvs"]["state"], rows["com.tcl.ttvs"]["policy"]),
                         ("enabled", "protected"))
        self.assertEqual(result["performance"]["home_pss_kib"], 123456)
        self.assertEqual(result["performance"]["janky_frames_since_reset"], 20)

    def test_failed_or_inconsistent_inventory_never_means_absent(self):
        for raw in (None, "", "Error: offline", "java.lang.SecurityException", "package:a.b;reboot"):
            with self.subTest(raw=raw):
                result = audit.report(dict(self.sample, packages=raw), self.rules)
                self.assertFalse(result["inventory_verified"])
                self.assertTrue(all(r["state"] == "unknown" for r in result["packages"]))
        result = audit.report(dict(self.sample, disabled="package:missing.package"), self.rules)
        self.assertFalse(result["inventory_verified"])

    def test_empty_disabled_list_is_valid(self):
        result = audit.report(dict(self.sample, disabled=""), self.rules)
        self.assertTrue(result["inventory_verified"])
        self.assertEqual(result["packages"][0]["state"], "enabled")

    def test_android_framework_package_is_valid_inventory(self):
        data = dict(self.sample, packages=self.sample["packages"] + "package:android\n")
        self.assertTrue(audit.report(data, self.rules)["inventory_verified"])
        self.assertEqual(audit.packages("package:android\n"), {"android"})
        self.assertIsNone(audit.packages("package:android;reboot\n"))

    def test_diagnostics_and_unrequested_private_values_are_not_persisted(self):
        data = dict(self.sample, frames="Error: private-device-id",
                    home_memory="No process found for: io.freemytv:home",
                    memory=self.sample["memory"] + "PrivateThing: secret-account\n")
        result = audit.report(data, self.rules)
        rendered = json.dumps(result)
        self.assertNotIn("private-device-id", rendered)
        self.assertNotIn("secret-account", rendered)
        self.assertIsNone(result["performance"]["frames_since_reset"])
        self.assertIsNone(result["performance"]["home_pss_kib"])

    @patch.object(audit.subprocess, "run")
    def test_only_fixed_read_commands_use_explicit_target(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "ok", "")
        audit.collect("/path/to/adb", "example.invalid:5555")
        observed = [c.args[0] for c in run.call_args_list]
        self.assertEqual(observed, [["/path/to/adb", "-s", "example.invalid:5555", "shell", command]
                                    for command in audit.QUERIES.values()])
        for call in run.call_args_list:
            self.assertNotIn("shell", call.kwargs)
            self.assertEqual(call.kwargs["timeout"], 20)
            self.assertRegex(call.args[0][-1],
                             r"^(getprop ro\.(product\.(model|device)|build\.version\.(release|sdk)|adb\.secure)"
                             r"|pm list packages( -d)? --user 0|cat /proc/meminfo"
                             r"|dumpsys (meminfo|gfxinfo) io\.freemytv:home)$")

    @patch.object(audit.subprocess, "run")
    def test_timeout_and_nonzero_fail_closed(self, run):
        run.side_effect = [subprocess.TimeoutExpired("adb", 20)] + [
            subprocess.CompletedProcess([], 1, "misleading output", "private-device-id")
        ] * (len(audit.QUERIES) - 1)
        self.assertTrue(all(v is None for v in audit.collect("adb", "target").values()))

    def test_policy_format_change_fails_visibly(self):
        with self.assertRaises(ValueError):
            audit.policy("// no declarations")


if __name__ == "__main__":
    unittest.main()
