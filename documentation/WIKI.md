# Publishing the Vibe TV wiki

[Open the wiki](https://github.com/pukrvi/vibe-tv/wiki) · [Contribution guide](../CONTRIBUTING.md)

GitHub stores the wiki in **`pukrvi/vibe-tv.wiki.git`**, separately from the app repository. A normal app/documentation PR cannot directly publish wiki pages. The former one-page welcome was a publication gap, not an app merge conflict.

The reviewed source lives in [wiki/](wiki/). `tools/sync_wiki.py` builds those pages from the primary guides and rewrites relative file links to wiki pages or repository files. Performance images are copied with the pages, so wiki imagery does not depend on an unmerged app branch. The wiki publisher owns those generated page names; unrelated wiki pages are preserved.

## Refresh and review

```sh
python3 tools/render_performance.py
python3 tools/sync_wiki.py
python3 -m unittest tools/test_performance_assets.py -v
```

Commit the primary guides, generated wiki pages and performance assets together in a linked documentation PR. Keep all snapshot caveats and provenance intact. Primary documentation is the editing source; direct wiki edits should be brought back into that source before regenerating, so publication does not silently overwrite them.

## Publish the separate wiki

With your normal GitHub Git authentication configured:

```sh
git clone https://github.com/pukrvi/vibe-tv.wiki.git /tmp/vibe-tv-wiki
python3 tools/sync_wiki.py --destination /tmp/vibe-tv-wiki
git -C /tmp/vibe-tv-wiki diff --check
git -C /tmp/vibe-tv-wiki add --all
git -C /tmp/vibe-tv-wiki commit -m 'Publish current Vibe TV guides and performance visuals'
git -C /tmp/vibe-tv-wiki push origin HEAD
```

Inspect the wiki diff and fetch its latest remote state before publishing. If other people changed the generated pages, reconcile those changes into the source guides first. The destination writer never runs Git or pushes by itself, and preserves unrelated destination files. Validate the public Home page, navigation and performance images after pushing. A repository PR being merged is not evidence that the wiki was published.

## Branch notice versus conflicts

GitHub's “ahead of / behind” notice compares this fork's history with upstream Free My TV. It does not by itself mean that a PR has conflicts. This documentation update deliberately retains Vibe TV's upstream baseline; upstream integration and app compatibility testing are separate work. Use a clean docs branch and squash its PR instead of rewriting already published main history to remove that notice.
