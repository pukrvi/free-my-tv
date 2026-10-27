# Read-only TV privacy and performance audit

This developer/support tool incorporates the evidence-based inventory approach from the
[CleanYourTV review](CLEANYOURTV_REVIEW.md) into Vibe TV without replacing its interface, settings,
playback history, or recovery mechanism. It uses Python 3 and an existing Android platform-tools
installation. It installs nothing, presses no keys, changes no settings, clears no caches, and
does not disable packages. Running ADB can start the computer's ADB server; the target must already
be connected and authorized. It does not connect, pair, or scan the network for a TV.

Use the saved target from the local device runbook, after authorization for device inspection:

```sh
python3 tools/tv_audit.py --adb /path/to/platform-tools/adb --serial <connected-target> --out /tmp/tv-audit-before.json
```

Do not put the real target address in committed examples. The tool omits that target from its
output and refuses to overwrite a previous report. Reports still reveal model, package state,
and memory statistics; review them before sharing. It never requests identifiers, advertising IDs,
accounts, notifications, viewing history, network dumps, or the private build configuration.

The output separates presence/enabled state from this revision's literal `HomeMode.DEBLOAT` and
`PROTECTED` lists. “Already targeted” is policy, not proof that a package is currently disabled.
“Protected” is not an invitation to override the guard. “Review only” never generates a disable
command. Runtime guards (Wi-Fi ownership, input hardware, setup/Home ordering) remain authoritative.
The catalogue covers selected TCL and Google candidates, not every possible tracker or core service.

An unavailable query is `null`/`unknown`. Missing, empty, malformed or inconsistent full inventory
results make every package state unknown. A valid inventory can establish that a package is absent;
it cannot establish that all tracking has stopped. AppOps permissions alone cannot prove recording
or uploading, particularly with shared system UIDs. This tool deliberately makes no such inference.

Memory values use KiB. Frame counters are cumulative since Android's last graphics-counter reset,
potentially spanning different UI states or processes. No counters are reset. These numbers do not
measure key latency or prove an optimization improved performance. Compare controlled intervals
only with a separately approved measurement procedure; do not divide two unrelated lifetime totals.
An inactive `:home` process or unsupported firmware output legitimately yields `null`.

For offline analysis, `--snapshot input.json` accepts an object whose keys are entries in `QUERIES`
in [tv_audit.py](../tools/tv_audit.py), and values are the corresponding query output strings or `null`.
Unknown query names and non-string values are refused. Raw snapshots can contain sensitive data;
keep them outside the repository. Reports contain only selected parsed fields, never raw dumps.
Exit status is 0 for a verified package inventory, 1 for unknown inventory, and 2 for tool/input/output
errors. Status 0 does not certify privacy or performance.

Run the offline contract checks without connecting to a TV:

```sh
python3 -m unittest discover -s tools -p 'test_tv_audit.py'
```

Before adding a candidate to automatic HomeMode cleanup, verify its exact role on that firmware,
record initial state, use the existing reversible disable path, and check HDMI/source menus,
Bluetooth pairing, casting, voice, playback, reboot persistence, and Google TV/Original restoration.
Keep supported manual privacy consent choices separate from launcher restoration. Further platform
controls and their limits are documented in [the primary-source checks](ANDROID_PRIVACY_SOURCES.md).
