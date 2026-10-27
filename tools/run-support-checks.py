#!/usr/bin/env python3
"""Signature-bound maintenance helper; requires explicit owner authorization.

Checkpoints contain private app data and keys. Keep them outside the repository.
The helper APK is always removed; restoring never uninstalls the main app.
"""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("action", choices=["checkpoint", "restoredata", "verifyrestore", "cleanup", "restorecleanup", "inspectcleanup", "metrics", "artcheck"])
    p.add_argument("--serial", required=True)
    p.add_argument("--keystore", required=True, type=Path)
    p.add_argument("--checkpoint", type=Path)
    args = p.parse_args()
    if args.action in ("checkpoint", "restoredata", "verifyrestore") and not args.checkpoint:
        p.error("Checkpoint path required")
    if args.action == "checkpoint" and args.checkpoint.exists():
        p.error("Checkpoint already exists")
    repo = Path(__file__).resolve().parents[1]
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    bt = max((sdk / "build-tools").iterdir(), key=lambda x: [int(v) for v in x.name.split(".")])
    jdk = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"))
    os.environ["JAVA_HOME"] = str(jdk)
    jar = sdk / "platforms/android-34/android.jar"
    adb = [str(sdk / "platform-tools/adb"), "-s", args.serial]

    def run(cmd, data=None):
        r = subprocess.run([str(s) for s in cmd], capture_output=True, check=True, input=data, timeout=120)
        return r.stdout.decode("utf-8", "replace")

    installed = False
    with tempfile.TemporaryDirectory(prefix="vibe-support-") as folder:
        folder = Path(folder)
        try:
            (folder / "classes").mkdir(); (folder / "dex").mkdir()
            (folder / "AndroidManifest.xml").write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.freemytv.supportchecks">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="33" />
<application android:label="Vibe TV support checks" android:hasCode="true" android:allowBackup="false" />
<instrumentation android:name="io.freemytv.SupportChecks" android:targetPackage="io.freemytv" android:targetProcesses="io.freemytv" />
</manifest>''')
            run([bt / "aapt2", "link", "-o", folder / "base.apk", "-I", jar, "--manifest", folder / "AndroidManifest.xml"])
            run([jdk / "bin/javac", "-source", "8", "-target", "8", "-Xlint:-options", "-cp", jar,
                 "-d", folder / "classes", repo / "tools/tests/SupportChecks.java"])
            classes = list((folder / "classes/io/freemytv").glob("*.class"))
            run([bt / "d8", "--lib", jar, "--output", folder / "dex", *classes])
            with zipfile.ZipFile(folder / "base.apk", "a") as apk:
                apk.write(folder / "dex/classes.dex", "classes.dex")
            run([bt / "zipalign", "-f", "4", folder / "base.apk", folder / "aligned.apk"])
            # Existing local development key; password supplied through env if customized.
            env = dict(os.environ)
            env.setdefault("FREEMYTV_KEYSTORE_PASS", "freemytv")
            subprocess.run([str(bt / "apksigner"), "sign", "--ks", str(args.keystore), "--ks-pass",
                            "env:FREEMYTV_KEYSTORE_PASS", str(folder / "aligned.apk")],
                           env=env, capture_output=True, check=True)
            run(adb + ["install", "-r", "--no-incremental", folder / "aligned.apk"])
            installed = True
            extra = ""
            if args.action in ("restoredata", "verifyrestore"):
                data = args.checkpoint.read_bytes()
                for offset in range(0, len(data), 24576):
                    chunk = base64.b64encode(data[offset:offset + 24576]) + b"\n"
                    # Secret bytes travel on stdin, never on a host command line or shared TV storage.
                    command = 'read payload; am instrument -w -e action restorechunk -e offset ' + str(offset) + ' -e chunk "$payload" io.freemytv.supportchecks/io.freemytv.SupportChecks'
                    answer = run(adb + ["shell", command], chunk)
                    if "RESTORE_CHUNK_OK" not in answer:
                        raise RuntimeError("Private restore transfer failed")
                extra = " -e digest " + hashlib.sha256(data).hexdigest()
            output = run(adb + ["shell", "am instrument -w -e action " + args.action +
                               extra + " io.freemytv.supportchecks/io.freemytv.SupportChecks"])
            marker = {"checkpoint": "CHECKPOINT_OK", "restoredata": "RESTORE_DATA_OK",
                      "cleanup": "CLEANUP_OK", "restorecleanup": "CLEANUP_RESTORED", "inspectcleanup": "CLEANUP_STATES",
                      "metrics": "METRICS_OK", "artcheck": "ARTCHECK_OK", "verifyrestore": "RESTORE_VERIFIED"}[args.action]
            if marker not in output or "SUPPORT_FAILED" in output:
                if args.checkpoint:
                    log = args.checkpoint.with_suffix(".support-log")
                    with log.open("w") as dest:
                        os.chmod(log, 0o600); dest.write(output)
                status = re.findall(r"^INSTRUMENTATION_(?:RESULT|CODE):.*$", output, re.M)
                status += re.findall(r"SUPPORT_FAILED [A-Za-z0-9_.]+", output)
                raise RuntimeError("Support instrumentation failed: " + " ".join(status))
            if args.action == "checkpoint":
                chunks = re.findall(r"^INSTRUMENTATION_STATUS: checkpoint_chunk=([A-Za-z0-9+/=]+)$", output, re.M)
                if not chunks:
                    raise RuntimeError("No checkpoint returned")
                data = b"".join(base64.b64decode(x, validate=True) for x in chunks)
                with args.checkpoint.open("xb") as dest:
                    os.chmod(args.checkpoint, 0o600); dest.write(data)
                with zipfile.ZipFile(args.checkpoint) as archive:
                    if archive.testzip() is not None:
                        raise RuntimeError("Checkpoint is corrupt")
                print("Private checkpoint saved and verified.")
            else:
                print(marker)
                if args.action == "inspectcleanup":
                    print(next(line for line in output.splitlines() if line.startswith("CLEANUP_STATES ")))
                if args.action == "artcheck":
                    print(next(line for line in output.splitlines() if line.startswith("ARTCHECK_OK ")))
        finally:
            if installed:
                run(adb + ["uninstall", "io.freemytv.supportchecks"])


if __name__ == "__main__":
    main()
