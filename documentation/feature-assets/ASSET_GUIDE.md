# Vibe TV feature assets and video handoff

## Deliverables

Each scene has an editable **1920 × 1080 SVG** and a matching PNG for README/wiki. The SVG uses named `<g id="…">` groups for independent animation. Typography, frames, captions, diagram elements and bars are vector shapes/text. **TV screenshots remain embedded raster layers**, not traced or invented vector recreations. Keep their original images and provenance.

| Scene | Master / preview | Evidence | Readiness |
|---|---|---|---|
| Continue Watching | `continue-watching.svg` / `.png` | Published TCL capture from runtime `3e382d0` | Row/artwork/progress illustration; provider resume limits remain |
| Performance | `performance.svg` / `.png` | `../performance/measurements.json` | Historical snapshots; not controlled three-way data |
| Ads and optional cleanup | `privacy-and-ads.svg` / `.png` | Stock-home upstream illustration + actual TCL home + optimization report | UI comparison; no claimed tracker count or universal blocking |
| Stremio catalogs | `catalog-addons.svg` / `.png` | Implementing thread's two draft adapter definitions | Draft diagram only; not a working-engine screenshot |
| Recommendation engine | `recommendations.svg` / `.png` once gated | Merged recommendation work + reviewed actual-device capture | Pending; no synthetic working screenshot |

## Visual system

- Canvas: 1920 × 1080, 16:9; 80 px safe area. Recompose for portrait later rather than cropping off claims.
- Palette: background `#101214`, surface `#1C1F22`, outline `#454B50`, white `#F5F5F4`, muted `#ADB3B8`, accent `#E8C97D`.
- Inter is the editable typeface, bundled at `docs/assets/fonts/InterVariable.ttf`; preserve its OFL notice. Headlines 65–80 px, body 27 px, evidence/disclosures 20–25 px. Export with the font installed; keep an editable-text master even if a delivery copy outlines glyphs.
- Match Vibe TV's charcoal surfaces, clear white focus outline, generous spacing and restrained highlight. Use the gold only to guide attention.
- Keep screenshot aspect ratio. Resize only; no generated controls, fabricated titles, fake playback positions or replacement art. When a scene needs a detail crop, label it and retain the full source capture.
- Give each scene one clear idea. Keep evidence dates, conditions, fallback states and app-specific limits close to the claim. Captions/disclosures are part of the composition, not removable fine print.

## Source and publication rules

`evidence.json` is the provenance and readiness register. A new engine scene requires its implementation on fetched `origin/main`, reviewed screenshot, approved copy, app-routing details and limitations. The renderer skips the recommendation scene while it is pending. A passing PR or installed experimental build is not the same as a landed feature.

Use distinct descriptions for an actual device capture, a stock-home illustration and a conceptual diagram. The current privacy comparison uses different sources: never caption it as the same TV before and after. Optional package cleanup is not a measured tracker-blocking service. Never publish “all trackers removed”, “zero tracking”, “all in-app ads blocked” or a percentage without direct evidence.

Position recommendations and Continue Watching as headline features. Claims such as “first”, “only” or “no other TV app does this” require a separately documented competitor review; they are not supported by our current tests.

For screenshots, review account names, location, viewing history and credentials before publication. Prefer public test media and the already reviewed public gallery. Streaming artwork/logos remain their owners' material; do not treat a screenshot as a license to redistribute separate promotional artwork.

## Rebuild

```sh
python3 tools/render_feature_assets.py
```

Requires Python 3 and Pillow, as does the performance renderer. Scene geometry is shared by the PNG and SVG exporters. SVG text retains Inter font references; its screenshot images are embedded for portability. No JavaScript, remote resources or private metadata is embedded. For video, import the SVG master or its named groups into the editor; the embedded screenshots can move/scale as independent layers.

## One-minute video plan

| Time | Story beat | Asset / motion | Evidence guardrail |
|---|---|---|---|
| 0–5 s | Vibe TV + tagline | Brand title and a calm full-home reveal | Actual final build; no invented TV interaction |
| 5–18 s | Find something worth watching | Recommendation scene; reveal catalog/source labels, then focus one card | Wait for merged engine and real route/opening recording |
| 18–31 s | Keep watching | Continue scene; reveal title/art/progress groups, then a real saved-card opening | Show supported app path; don't imply universal one-press YouTube resume |
| 31–42 s | Less waiting, more room | Performance SVG; animate bars from zero, reveal values and conditions | Historical snapshots stay labeled; no fake 60 fps claim |
| 42–48 s | Two catalog sources | Catalog add-ons diagram | Replace draft label only after the two adapters land and are tested |
| 48–55 s | A quieter home and optional cleanup | Stock illustration / Vibe capture, then reversible-cleanup label | No matched tracker test or measured blocked count is implied |
| 55–60 s | Try it / contribute | Tagline, repository URL, end frame | Use current project URL and credits |

Use brief 250–400 ms opacity/position transitions, restrained focus movement and no unnecessary camera shake. Animate named groups rather than flattening the scene; keep captions visible long enough to read. Record final UI interactions only after coordinated TV access and feature acceptance. Add narration, sound, captions and final timing later; this handoff does not claim that the one-minute video has already been produced.
