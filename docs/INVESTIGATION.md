# Investigation — 67movies.st

Live log of what was found on the site before/during implementation.
Evidence: `curl` HTML of `https://67movies.st/`, in-app browser DOM +
network inspection, and `_next` chunk inspection. Dates: 2026-11-06.

## What the site is

- **67movies.st** — Next.js (React Server Components) streaming-catalogue
  site. Title: *"67movies.st — Watch Movies & TV Shows in HD Online"*.
  Cards served from the **TMDB API** (`api.themoviedb.org/3/...` with an
  embedded public API key, fetched client-side).
- **Catalogue is RSC JSON, not HTML cards.** The home page (1.5 MB SSR
  HTML) contains `self.__next_f` payloads with full item objects
  (`id`, `title`/`name`, `poster_path`, `type`/`media_type`,
  `vote_average`, `genres`, …) grouped into `featuredRails` (one array per
  rail, ~35–40 items each). The visible cards (`div.rail-card`) are
  rendered client-side from that data; `rail-card` does not appear in
  raw HTML at all. **Harness consequence:** the div-block HTML scraper
  finds nothing. Extraction must parse the RSC JSON (see `ISSUES.md`
  entry I-1).
- **Rails on home (keys):** `trending-movies`, `trending-series`,
  `popular-tmdb`, `top-rated-movies`, `top-rated-series`,
  `steamy-movies`, `steamy-series`, `action-movies`, `sci-fi-movies`,
  `crime-thrillers`, `romantic-movies`, `horror-movies`, `uhd-films`,
  `uhd-series`, … Some rails are inline (`"items":[{...}]`); a few are
  references (`"items":"$3:props:children:..."`) and do **not** carry
  data in the HTML (e.g. `steamyMovies`). Chosen tabs use inline ones.
- **Top-level routes:** `/` (home) and `/anime` (placeholder — *"Anime
  section coming soon"*). No `/movies`, `/series`, `/genre`, `/browse`
  pages (`/movies` and `/series` return 404). **No category pages exist**,
  so the harness `genres` tab is removed from `TABS` for this site.
  Categories that a TV user would expect are simply the rails themselves.

## Item (details) pages

- **`/details/movie/<tmdb_id>`** and **`/details/tv/<tmdb_id>`**
  (from `sitemap.xml`; `/title/<id>` and `/title/<slug>` are **not**
  routes — those strings are Netflix `homepage` fields in the JSON).
- Details page = modal overlay (`.dm-shell`, fixed inset-0) over the
  underlying page. It renders after a client fetch to TMDB
  (`api.themoviedb.org/3/movie/155?...&api_key=...&
  append_to_response=credits,...`); initial DOM shows *"Preparing
  details..."* until that returns. Footer: 1× `footer` element.
- **Play flow (loaded in a real browser):** click **Play** →
  an iframe appears: `https://player.vidqueen.st/embed/movie/155?...`
  (movie) or `.../embed/tv/1396/1/1?...` (TV, season/episode in URL).
  Server card shows **"VidQueen  4K — Best sync • Skip intro"** (single
  server; no server list). Below it: movie shows server UI only; TV
  shows season `<select>` (Specials, Season 1..N) + episode list
  (clickable cards, "E1 · NOW PLAYING").
- **Play button hook:** `<button class="bg-white text-black px-9 ...">`
  with a `<span>Play</span>` / `Resume` / `Play in 4K` label. No id,
  no aria-label. Selector used: `button.bg-white.text-black`.
- TV Play defaults to **season 1 episode 1** (or the resumable episode
  from local progress). Changing season/episode is a click on the
  site's season `<select>` / episode card — the harness has no control
  for it, so TV season switching is a known limitation (see ISSUES.md).

## Ads / trackers

- Inline script hires a pseudo-random sub-domain ad loader
  (`*.cfd` / `*.rest` hosts, `advertiser` div, `window.open` wrapper
  that opens a popup form on first mousedown unless ad-detector
  div (`r.offsetHeight === 0`) trips). **AdBlocker must keep blocking
  these and harness `window.open` stubbing matters.**
- `api.themoviedb.org`, `image.tmdb.org`, `player.vidqueen.st`,
  `www.youtube.com` (+ `i.ytimg.com`, season-recap embeds) must all be
  in `FIRST_PARTY_HOSTS` so neither poster loading in the WebView nor
  the details fetch nor the embed ever gets blocked.
- Posters: `https://image.tmdb.org/t/p/w500<poster_path>` (direct) or
  the site proxy `/ _next/image?url=...`; the harness constructs the
  TMDB URL itself, so `ALLOWED_IMAGE_PREFIXES = image.tmdb.org`.
- `sitemap.xml`: only `/`, `/anime`, and thousands of
  `/details/movie|tv/<id>` URLs. `robots.txt` is Cloudflare-managed
  content signals (ai-train=no etc.) — irrelevant to this client.

## Implementation plan (fork-only)

1. `CatalogScraper.fetchItems`: keep the HTTP fetch; after unescaping
   the RSC payload (ordered `\\\\`→`\\` then `\\"`→`"`), find the rail
   named by the tab path's `?rail=<key>` query, bracket-match its
   `"items":[...]` array, and parse with `org.json.JSONArray`.
   Item URL = `/details/{type}/{id}`; poster = TMDB w500.
   HTML div-block mode is kept as fallback when the path has no rail.
2. `SiteConfig`: 6 rail tabs, no genres tab, player selectors for the
   modal + Play button, inert episode selectors.
3. `WatchActivity`: guard empty selectors with toasts (generic bug fix,
   backported to main afterwards) — see `ISSUES.md`.
4. Build with a throwaway keystore; smoke-test on a real Fire TV
   (AFTSS, Android 9): catalogue renders (221 titles), card opens
   details, OSD PAUSE/PLAY starts the embed, back returns to catalogue.

## Status after testing (2026-11-06, Fire TV AFTSS)

- Catalogue: works (all 6 rails, 221 titles, posters, details text).
- Details modal: works; Play via OSD works (I-11 workaround).
- Video frames: **not verified** — `api.vidqueen.st` returns
  `source:null` from this network (I-13); the embed UI loads and is
  correctly wired, but the media server serves nothing here. Re-test
  video playback from a network the server serves.
- TV seasons: limitation (I-8) — Play starts S1E1/resume.
