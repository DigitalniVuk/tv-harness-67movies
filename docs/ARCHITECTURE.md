# Architecture

## App flow

`CatalogActivity` is the launcher screen. It fetches every configured
tab page (`SiteConfig.TABS`) plus the category discovery page over HTTPS
on a single background executor. `CatalogScraper` parses each page's HTML
into `CatalogItem`/`CatalogCategory` records without running website
JavaScript; all endpoints, classes, and regexes come from `SiteConfig`.
The activity builds native Android TV cards and loads posters on a small
worker pool.

Selecting an item opens `WatchActivity` with the item URL. That activity
hosts the site's player page in a WebView, customises it with CSS built
from `SiteConfig` player selectors, and supplies native, focusable
controls for the TV remote (pause, prev/next, section picker, autoplay,
reload, browse). `AdBlocker` filters WebView navigation and resource
requests against the bundled host list and URL path rules, never blocking
hosts in `SiteConfig.FIRST_PARTY_HOSTS`; known media file paths always
pass so filtering cannot break streams.

## Refresh and caching

Refresh reloads every tab and the category page. Catalogue requests send
no-cache headers, disable URLConnection caching, and include a unique
query parameter to avoid stale intermediary responses. Refresh also
clears the activity's in-memory poster bitmap cache. Poster downloads use
no-cache headers and run off the UI thread.

## Main components

| Component | Responsibility |
| --- | --- |
| `SiteConfig` | **The only site-specific file.** Endpoints, item selectors, player hooks, host allowlists. Edit only this to plug in a website (see `docs/AGENT_GUIDE.md`). |
| `CatalogActivity` | Catalogue UI, remote navigation, refresh, poster loading, opening playback. Tabs from `SiteConfig`. |
| `CatalogScraper` | Site-agnostic HTTPS fetching and HTML extraction. |
| `CatalogItem` | Title, item URL, poster URL, details. |
| `CatalogCategory` | Category label and path. |
| `WatchActivity` | WebView playback and native player controls, driven by `SiteConfig` selectors. |
| `AdBlocker` | Ad domain/URL checks for WebView requests; first-party hosts from `SiteConfig`. |
| `App` | Process-level application context for bundled filter loading. |

## Boundaries

- The app needs internet access only and no user account.
- Catalogue pages are parsed as HTML data; their scripts are not run in
  the catalogue screen.
- The playback screen must load the configured website's player, so site
  behaviour and playback availability remain controlled by that site and
  its providers.
- The bundled ad list is a build-time snapshot; it is not automatically
  updated while the app runs.
- Nothing site-specific exists outside `SiteConfig.java`
  (`scripts/check-site-refs.sh` enforces this).
