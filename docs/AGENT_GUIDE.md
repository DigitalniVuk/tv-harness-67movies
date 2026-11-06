# Agent guide — plug any website into the harness

Read `docs/API.md` (endpoints + JS contract) and `docs/REQUIREMENTS.md`
(site prerequisites) before starting. The whole job is editing
`src/tv/harness/SiteConfig.java`. Do not edit any other file. If you think
you must, stop: the harness already handles your case, or you have found
a real limitation — note it in the harness plan under "Phase 4".

## Step 0 — Copy the template

`src/tv/harness/SiteConfig.java` is already the template; it ships filled
with `example.com` TODOs. Keep a pristine copy (e.g. `git checkout
SiteConfig.java` restores it). Never commit a filled-in real site's values
back as the template.

## Step 1 — Fix identity and origin

1. `DISPLAY_NAME` -> the site's public name.
2. `BASE_URL` -> `https://<host>` (no trailing slash). `HOME_PATH` -> the
   site's home page path.
3. Add every first-party host (site + any media/CDN host the player uses)
   to `FIRST_PARTY_HOSTS`, and every poster host prefix to
   `ALLOWED_IMAGE_PREFIXES`. Missing a media host = blocked playback;
   missing a poster host = blank cards.

## Step 2 — Fill the catalogue endpoints

For each page you want a tab for, note its path and add it to `TABS`
(keep `genres` with `path == null`, keep the `home` id first — see the
tab-id contract in `API.md`). Two tabs may share a path; the harness
fetches each tab separately.

Verify each page: `curl -s -A '<USER_AGENT>' <BASE_URL><path>` and check
items appear in the raw HTML.

## Step 3 — Fill item extraction

From one saved listing page, find one item's HTML in the browser
(View Source, search for a known title) and set:

- `ITEM_CLASS` — the whole-word class on the item's outer `<div>`
  (harness scans `<div>` blocks by balanced depth; `<li>`/`<article>`
  items are NOT supported — Phase 4).
- `ITEM_LINK_PATH` — the substring every item link contains (e.g.
  `/watch/`). The first such anchor in the block becomes the item URL.
- `ITEM_TITLE_CLASSES` — anchor class(es) holding the title.
- Poster: first `<img>` in the block (`data-src` then `src`).
- `ITEM_TYPE_REGEX` / `ITEM_RATING_REGEX` / `ITEM_EPISODES_REGEX` — copy
  the surrounding HTML, replace it with a regex whose group 1 is the text
  you want, using `(?is)` and non-greedy `.*?`. Or set `""` and remove
  the element from the card entirely. Keep `ITEM_DEFAULT_TYPE` as the
  fallback label.

Validation: paste one item block + each regex into any regex tester
(Python `re` with `re.I|re.S` matches Java here) and confirm group 1.

## Step 4 — Categories (skip if the site has none)

Set `CATEGORY_DISCOVERY_PATH` (usually `/home` or a genre index page)
and `CATEGORY_PATH_PREFIX` (e.g. `/genre/`). The harness reads slugs and
labels off anchors on that page. Category pages are parsed with the same
item rules as Step 3. No categories? Delete everything except the
`path == null` tab trick, or point the discovery path at a page with no
matching anchors (empty grid is handled).

## Step 5 — Fill the player

Open the item/player page in a desktop browser and inspect:

- `PLAYER_HIDE_CSS` — selectors for header/footer/sidebar/comments/ads.
  Everything hidden is rebuilt by the harness's own OSD.
- `PLAYER_KEEP_SELECTORS` / `PLAYER_ELEMENT_SELECTOR` /
  `PLAYER_FRAME_SELECTOR` — the player container chain. The harness
  force-fills and 16:9-centres these.
- `PLAYER_FOCUS_SELECTOR` — the site's play button (harness focuses it
  on load so D-pad OK starts playback).
- `EPISODE_*` / `PREV_` / `NEXT_` / `AUTOPLAY_` selectors — the section
  picker and transport controls. If the site has no sections, point
  `EPISODE_LIST_SELECTOR` at something nonexistent (e.g. `"#none"`) —
  the picker stays inert instead of guessing.
- `PAUSE_TOGGLE_SELECTOR` — clickable pause control if the site has one,
  else `""` (the harness dispatches Space; verify the site pauses on
  Space in a real WebView).

Then actually test in the WebView (build + `adb install`, or the
emulator): catalogue renders, card OK opens the player, video plays,
prev/next and the picker (if any) work, back returns to the catalogue.

## Step 6 — Validate the no-leak rule

Run `./scripts/check-site-refs.sh`. It must pass. Any failure means a
site string leaked outside `SiteConfig.java` — move it into the config;
if the config has no home for it, that is a Phase-4 harness change, not
a local hack.

## Common failures

| Symptom | Likely cause |
|---|---|
| Empty catalogue, no error | Listing page renders items via JS, or `ITEM_CLASS`/`ITEM_LINK_PATH` wrong. Re-check with `curl` raw HTML. |
| Cards without posters | Poster host missing from `ALLOWED_IMAGE_PREFIXES`, or poster uses `srcset` (unsupported — pick a different `<img>` or extend the scraper; Phase 4). |
| Player shows site chrome | `PLAYER_KEEP_SELECTORS`/`PLAYER_HIDE_CSS` incomplete. |
| Video does not play | Missing media host in `FIRST_PARTY_HOSTS`, or `PLAYER_FRAME_SELECTOR` empty/wrong so the video sits at 0×0. |
| Pause button dead | Site ignores Space; set `PAUSE_TOGGLE_SELECTOR` to its pause control. |
| Episode picker empty forever | Wrong `EPISODE_LIST_SELECTOR`/`EPISODE_NUM_ATTR`; devtools the anchors and re-copy. |
