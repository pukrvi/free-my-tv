# Vibe TV website documentation

The English and Portuguese pages now identify Vibe TV and point to the current project documentation. The marketing demos, measurements, media, webOS repository metadata and old installation copy beneath the status notice are inherited from Free My TV. They are explicitly labeled as upstream material; they are not a Vibe TV release or current hardware verification.

Current documentation lives in the repository root: [README](../README.md), [installation](../INSTALLATION.md), [features](../FEATURES.md), [compatibility](../COMPATIBILITY.md), [contributing](../CONTRIBUTING.md), [privacy](../PRIVACY.md) and [screenshots](../documentation/SCREENSHOTS.md).

`docs/index.html` and `docs/pt/index.html` must stay consistent. The GitHub Pages workflow publishes this directory when changes reach `main`. The inherited workflow is configured for pushes to main, but this fork has no GitHub Pages site configured (`has_pages=false` at integration). No hosted website deployment was observed. Repository documentation and screenshots are available on GitHub; website hosting requires separate setup. No Vibe TV domain is configured or claimed; `freemytv.io` belongs to upstream. The existing icon/social graphics and `docs/assets/` demos retain upstream artwork until a runtime/visual identity migration.

`docs/webos/apps.json` and the packaged IPK are inherited distribution artifacts. Do not relabel or redirect signed artifacts as a new Vibe TV release. Replace them only as part of an intentional, verified release with matching hashes and provenance.
