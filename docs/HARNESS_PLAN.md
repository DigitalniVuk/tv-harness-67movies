# Harness conversion plan

Goal: turn the single-site Android TV client into a reusable **website ->
TV app harness** where an agent plugs in any suitable website by editing
one documented file. Everything site-specific must be removable without
touching harness code.

## Audit: where the old single-site coupling lived

All site coupling was found by grepping the source for the site's origin,
listing paths, markup selectors, host names, and brand strings:

| # | Coupling | Where it was |
|---|---|---|
| 1 | Site origin, `HOME_URL`, and URL validation (the old hardcoded origin) | `CatalogScraper`, `WatchActivity.startUrl` |
| 2 | Listing paths `/latest-updated`, `/most-viewed` | `CatalogActivity.loadCatalog` |
| 3 | Category discovery `/home`, prefix `/genre/` | `CatalogScraper.fetchGenres` |
| 4 | Item markup: `div.item`, `/watch/` links, `name`/`d-title` titles, `right`/`rated`/`ep-status sub` details regexes, `data-src` posters | `CatalogScraper` patterns |
| 5 | Poster CDN host prefix (the old hardcoded CDN origin) | `CatalogActivity.loadPoster` |
| 6 | Player markup: `#w-episodes`, `#w-player`, `#player`, `.ctrl.prev`/`.next`, `#controls .auto-play`, autoplay `localStorage` key | `WatchActivity` JS strings |
| 7 | Player chrome-hiding CSS selectors | `WatchActivity.PLAYER_PAGE_SCRIPT` |
| 8 | First-party hosts (never ad-blocked) | `AdBlocker.FIRST_PARTY` |
| 9 | Brand strings and single-site copy, the old package name, the old launcher icon, the old APK filename, and the old `KEYSTORE*` env vars | manifest, activities, `build.sh` |

The non-coupled, reusable core (kept unchanged in behaviour): native
catalogue UI and remote navigation, background fetch worker + poster
worker pool + bitmap cache, refresh/cache-busting logic, WebView player
scaffold (fullscreen custom-view handling, D-pad OSD, auto-hide), the
`AdBlocker` domain/path rules, and `assets/blocking-domains.txt`.

## Target architecture

```
                    ┌──────────────────────────┐
                    │       SiteConfig.java    │  ◄── THE ONLY FILE AN
                    │ endpoints · selectors ·  │      AGENT EDITS (see
                    │ player hooks · hosts     │      docs/AGENT_GUIDE.md)
                    └─────────────┬────────────┘
                                  │ reads
        ┌─────────────────────────┼─────────────────────────┐
        ▼                         ▼                         ▼
CatalogActivity           CatalogScraper           WatchActivity
(native TV catalogue,     (site-agnostic HTTP      (WebView player +
 remote nav, posters)      fetch + HTML regex)      native remote OSD)
        │                         │                         │
        └────────── CatalogItem / CatalogCategory ──────────┘
                                  ▲
                    AdBlocker (first-party hosts from SiteConfig)
```

Design rules:

1. **One plug-in file.** No site hostname, path, selector, CSS, or JS
   string may exist outside `SiteConfig.java`. Enforced by
   `scripts/check-site-refs.sh` (see "Verification").
2. **Placeholders, not a sample site.** `SiteConfig` ships with
   `example.com` TODO values and builds against them, so the template
   cannot accidentally endorse a real site and cannot silently "work"
   against the wrong one. Empty string/selector disables that feature.
3. **Failure is silent-but-visible.** Bad selectors degrade features
   (item skipped, picker "still loading", toast on a missing control);
   they never crash or white-screen the app.
4. **No new dependencies.** Android SDK only; regexes stay in Java.

## Phases

### Phase 0 — Audit & baseline (done)
- Cloned the single-site app into this workspace and inventoried every
  coupling point (table above). Verified the original build compiled.

### Phase 1 — Uncouple code (done)
- Renamed the package to `tv.harness`; renamed
  the item/genre records to `CatalogItem`/`CatalogCategory`, the launcher
  icon to `app_icon`, the APK to `build/harness-tv.apk`, and the signing
  env vars to `HARNESS_*`; manifest label -> `TV Harness`.
- Added `SiteConfig.java` holding every row of the audit table.
- `CatalogScraper` reads origin, paths, classes, regexes from
  `SiteConfig`; `CatalogActivity` builds its tabs dynamically from
  `SiteConfig.TABS` (per-tab item storage) and checks posters against
  `ALLOWED_IMAGE_PREFIXES`; `WatchActivity` builds episode/autoplay/
  pause JS and the chrome-hiding CSS from `SiteConfig` selector
  constants; `AdBlocker` takes first-party hosts from `SiteConfig`.
- Compile-verified with `aapt2` + `javac --release 8` (see status).

### Phase 2 — Document the plug-in surface (done)
- `docs/API.md` — website endpoints, item-extraction rules, Java API,
  WebView JS contract.
- `docs/AGENT_GUIDE.md` — the playbook an agent follows for a new site.
- `docs/REQUIREMENTS.md` — what a target website must provide and the
  harness assumptions to kill first.

### Phase 3 — Guard rails (done)
- `scripts/check-site-refs.sh` — fails if any site-looking reference
  (URL literals, `href`/`data-num` selectors, host suffix lists) exists
  outside `SiteConfig.java` in `src/`.

### Phase 4 — Remaining work (not started, needs a real target site)
- **Fill `SiteConfig` for a real site** using `docs/AGENT_GUIDE.md`
  validation checklist (item regexes against saved HTML, player
  selectors live in a WebView). This is the first real plug-in test and
  the moment any placeholder assumption (e.g. `<div>`-based items) is
  validated or replaced.
- **Generalise item blocks beyond `<div class=item>`** if a target site
  uses `<li>`/`<article>`: add a `ITEM_BLOCK_TAG` constant and switch the
  balanced-tag scanner in `CatalogScraper.itemBlocks` to that tag only
  (mechanical change).
- **Keystore sample**: ship a debug-only unsigned/assemble path so
  agents can compile-check without a personal keystore (currently
  `build.sh` requires `HARNESS_KEYSTORE*` to sign).
- **Sample site fixtures**: save listing + player HTML fixtures under
  `docs/fixtures/` so regexes can be regression-tested in a unit test
  harness (this repo has no test runner today; that would be its first
  test).

## Status

- [x] Phase 0 audit & baseline clone
- [x] Phase 1 uncouple (package/rename, `SiteConfig`, config-driven
      scraper/activity/watch/ad-blocker)
- [x] Phase 1 compile check: `aapt2` + `javac --release 8` pass
- [x] Phase 2 docs (`API.md`, `AGENT_GUIDE.md`, `REQUIREMENTS.md`,
      this plan, updated `README.md`/`ARCHITECTURE.md`/`BUILDING.md`)
- [x] Phase 3 guard rails (`scripts/check-site-refs.sh`)
- [ ] Phase 4 first real plug-in (needs a chosen target site)
