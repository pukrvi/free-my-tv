# One set of guides, two places to read them

[GitHub Wiki](https://github.com/pukrvi/vibe-tv/wiki) reads from the same guides as the repository. Edit the source files only: README, installation, feature status, privacy and the guides under `documentation/`. The wiki generator changes links for GitHub and adds navigation. It does not maintain a second version of the prose.

Wiki Home comes from the illustrated [documentation index](INDEX.md), not the README. Images are served from their single repository location. Generated pages go to ignored `.wiki-build/` or a wiki checkout, not into another tracked documentation directory.

## Preview

```sh
python3 tools/sync_wiki.py
```

Review `.wiki-build/`. The page mapping is in `tools/sync_wiki.py`. Add a mapping when a new source guide needs a wiki page.

## Publish after the documentation merges

GitHub stores a wiki in a separate Git repository. A normal pull request cannot update it by itself.

```sh
git clone https://github.com/pukrvi/vibe-tv.wiki.git /tmp/vibe-tv-wiki
python3 tools/sync_wiki.py --destination /tmp/vibe-tv-wiki
git -C /tmp/vibe-tv-wiki diff --check
git -C /tmp/vibe-tv-wiki add --all
git -C /tmp/vibe-tv-wiki commit -m 'Refresh wiki from source guides'
git -C /tmp/vibe-tv-wiki push origin HEAD
```

Fetch and review wiki changes before publishing. Bring any direct edits to generated pages back into the source guides first. The generator preserves unrelated files, runs no Git commands and never pushes. Check public Home, navigation and image links afterward.

The old tracked `documentation/wiki/` copies have been removed. Existing wiki image copies can be removed once all pages use the repository image URLs.

## Branch history

GitHub's ahead/behind notice compares this fork with upstream Free My TV. It does not prove a pull request has conflicts. Upstream integration stays separate from documentation work; published main history is preserved.
