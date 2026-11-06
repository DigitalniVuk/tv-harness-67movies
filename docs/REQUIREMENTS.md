# Requirements

## What the target website must provide

The harness is deliberately dumb: it scrapes listing HTML and plays pages.
A website needs all of the following, or the corresponding harness feature
degrades or must be removed from `SiteConfig`.

- **Server-rendered listing pages.** Every catalogue page must contain the
  items in its initial HTML (no client-side rendering, no login wall, no
  bot challenge). Listing pages are fetched twice per refresh (cache-bust
  query parameter `__tv_refresh=<nanoTime>`) and must be idempotent.
- **Absolute or root-relative links.** Item URLs must be absolute or start
  with `/`; the harness resolves them against `BASE_URL` only.
- **Posters on a stable host.** Poster `<img>` URLs should be absolute
  `https://` and listable in `ALLOWED_IMAGE_PREFIXES`. Relative poster
  URLs are resolved but may fail the allowlist check.
- **A WebView-playable player page.** The item/player page must load with
  JavaScript and DOM storage enabled and play media without a tap. The
  site's player must be reachable from `document.querySelector` (see the
  selector contract in `docs/API.md`) and, for pause/resume, must listen
  to the Space key or expose a clickable pause control.
- **Episode/section anchors (optional).** Sites with a section picker need
  anchors matching `EPISODE_LIST_SELECTOR`/`EPISODE_ACTIVE_SELECTOR` with
  the episode number in `EPISODE_NUM_ATTR` or as anchor text. Sites with
  no sections leave these selectors pointing at nothing; the picker shows
  "still loading" and stays harmless.
- **HTTPS only.** `BASE_URL` must be `https://…`; WebView mixed content is
  blocked (`MIXED_CONTENT_NEVER_ALLOW`) and SSL errors are cancelled.
- **No account or paywall in the flow.** The harness has no credential
  store, no header/cookie injection, and no checkout. Sites requiring
  login cannot be supported without a larger change.

## Runtime requirements (harness side)

- **Android platform 25+** (min API 25, target 28), any 1080p+ TV or
  Fire TV class device; no touchscreen required (Leanback optional).
- **Java 21** to build (`javac --release 8` output), Android SDK build
  tools, a signing keystore (see `docs/BUILDING.md`).
- **Network:** outgoing HTTPS on 443 only. No account, no push, no
  analytics, no background services beyond the in-process fetch and image
  worker pools (1 + 3 threads), both shut down in `onDestroy`.
- **Storage:** in-memory only (poster bitmap cache ≤ 10 MB). Refresh
  clears the poster cache and sends `no-cache` headers; `AdBlocker`'s
  domain list is a build-time snapshot (`assets/blocking-domains.txt`)
  and is not refreshed at runtime.

## Assumptions the harness makes (kill these first if wrong)

1. Site markup uses `<div>` blocks per item (the scraper balances `<div>`
   tags; `<li>`/`<article>` items need a scraper change, not just config).
2. The player page uses a centred 16:9 frame; `PLAYER_ELEMENT_SELECTOR`
   CSS is forced to `aspect-ratio 16/9` with `overflow:hidden`.
3. The site does not use `window.open`/`alert`/`confirm` legitimately
   (the harness stubs/cancels them to stop popups).
4. Runtime tab ids `"home"`, `"genres"`, `"category"` stay as documented
   in `docs/API.md`; renaming them is a `CatalogActivity` code change.

## Non-goals

- No generic CSS-selector *engine*: the three details regexes stay regexes.
- No JS rendering of catalogue pages (a headless WebView scraper would be
  a new activity).
- No configurable-at-runtime site switching (all config is compile-time
  in `SiteConfig.java`); no settings UI for base URL, UA, or selectors.
- No automatic ad-list updates, DRM extraction, offline download, or
  cross-site aggregation.
