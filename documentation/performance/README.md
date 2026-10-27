# Vibe TV performance assets

Two 2400 × 1500 PNGs use the home UI's charcoal surfaces, Inter type, rounded cards and focused outline. Gold distinguishes the optimized reading.

- [Comparison](comparison.png): one comparison figure with two independently labeled metric scales. Milliseconds and MiB are never put on a shared numerical axis.
- [Shareable highlights](highlights.png): three large percentage changes; each names its own baseline.
- [Measurement data](measurements.json): exact published snapshots, provenance, unknown values and claim definitions.
- [Full source report](../TCL_G10_OPTIMIZATION.md): observed data, controlled tests and important limits.

Rebuild with Python 3 and Pillow:

```sh
python3 tools/render_performance.py
```

Keep baseline labels and the caveat inside any shared image. The snapshots are **not** a controlled three-way benchmark. In particular, the original 150 ms / 594 MiB / 124 MiB are retained historical readings, 219 ms / 680 MiB are an owner's earlier readings, and 90 ms / 782 MiB / 71 MiB follow optimization and a restart. “Vanilla” is not evidence of a separately tested pristine installation. The missing middle PSS value remains unknown.

The controlled warm-return comparison (80 → 63 ms; 7 returns each) is separate. Do not relabel the 59% snapshot difference as a controlled navigation speedup, or claim better cold start, typical frame times or universal device performance.
