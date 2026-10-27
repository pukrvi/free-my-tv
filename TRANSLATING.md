# Translating Vibe TV

[Project](README.md) · [Contributing](CONTRIBUTING.md)

Settings uses Vibe TV; the installed Android app label remains Free My TV. Keep package IDs and recovery commands intact when editing translations.

## Translations

On Google TV, LG and Samsung, each language is one file, [`app/assets/i18n/<lang>.json`](app/assets/i18n), keyed by the
English text in the code. *Automatic* follows the TV's language and uses any language that has a file (English otherwise); *Settings*, *Preferences*, *Language* lists them all. Available languages: English, Portuguese, Spanish, German, French, Italian, Dutch, Polish,
Swedish, Danish, Norwegian, Finnish, Czech, Romanian, Turkish, Russian, Ukrainian, Greek, Japanese, Korean, Simplified
and Traditional Chinese, Hindi, Indonesian, Vietnamese and Thai. Portuguese, Spanish, German, Russian, Thai and Turkish were translated by hand and are the
reference (fix them by hand); the others were machine-made, and corrections from native speakers are welcome.

- **Add a language:** `python3 tools/translate.py --new ca`, then fill `app/assets/i18n/ca.json` by hand, or with
  `ANTHROPIC_API_KEY` set, `python3 tools/translate.py --fill --lang ca` (only the strings go to the API).
- **After changing text in the code:** `python3 tools/translate.py` lists what each language lacks; `--fill` translates
  only that (the hand-translated ones, Portuguese, Spanish, German, Russian, Thai and Turkish, are left for a person
  unless `--lang` names them), `--prune` drops strings the code no longer uses.
  Run `python3 tools/translate.py --check` to validate all catalogues without writing files.
- Text built at runtime has placeholders (`"{n} min ago"`), so each language puts the words in its own order. A key
  like `"unit|Automatic"` is the same English in another place, where a language may need another word.
- Counts have plural forms: `"plural|one:{n} season|other:{n} seasons"` is translated with one form per plural
  category the language uses (Russian `"one:{n} сезон|few:{n} сезона|many:{n} сезонов|other:{n} сезона"`, Japanese
  just `"other:{n}シーズン"`), and Android's plural rules pick the one for each number.
- **Limits:** right-to-left languages (Arabic, Hebrew, Persian) would need the layout mirrored, so they aren't there
  yet. Chinese, Japanese, Korean and Hindi are drawn with the TV's system font (Inter has no such letters).
