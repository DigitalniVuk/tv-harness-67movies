# Issue log — 67movies.st site fork

Every issue encountered, its cause, and its resolution. Entries marked
**[→ main]** are candidates to backport into `tv-harness` (docs first,
code where noted). Site-fork fixes are described in `INVESTIGATION.md`.

## I-1. Catalogue items are not in the HTML (Next.js RSC JSON)

- **Symptom:** `CatalogScraper.fetchItems("/")` returned zero items —
  there is no `<div class="item">` anywhere in the 1.5 MB SSR HTML.
  The visible cards (`div.rail-card`) exist only after client render.
- **Cause:** this site renders catalogue rows from React Server
  Component flight data embedded in `self.__next_f` script payloads,
  not from server-rendered card divs. The harness `REQUIREMENTS.md`
  assumption ("server-rendered HTML") does not hold for RSC sites.
- **Resolution (fork):** `CatalogScraper` gained an RSC/JSON mode —
  bracket-match the rail's `"items":[...]` array and parse it with
  `org.json.JSONArray`. Item URL built as `/details/{type}/{id}`.
- **[→ main] Best practice:** support this as a first-class mode
  (`ITEM_FORMAT = "nextjs-json"`) in the harness scraper instead of a
  fork patch, and teach `AGENT_GUIDE.md` to check "RSC JSON vs HTML
  cards" as step 1 of every plug-in.

## I-2. RSC payload is double-escaped; naive unescape corrupts JSON

- **Symptom:** after `replace('\\"','"')`, `JSONArray` parsing failed
  mid-array (`Expecting ',' delimiter`) — overview text containing
  escaped quotes (`"inception"`) broke the parse.
- **Cause:** the payload encodes `\\` *and* `\"` (`\\\\\"` = literal
  backslash + quote in the data). Replacing `\"` first leaves a stray
  `\` that re-escapes the next quote and ends the string early.
- **Resolution (fork):** ordered decode — `\\\\ → \0`, `\" → "`,
  `\0 → \\` (placeholder = NUL, cannot occur in the payload).
- **[→ main] Trick worth documenting:** the unescape *order* is the
  whole trick. Add the exact 3-step decode to `AGENT_GUIDE.md`.

## I-3. `/title/<id>` looks like the item route but is not (404)

- **Symptom:** first grep of home HTML showed many `/title/81245964`
  links; fetching `/title/155` returned 404.
- **Cause:** those strings are TMDB `homepage` fields pointing at
  *Netflix* (`https://www.netflix.com/title/81245964`), and
  `/movies/dark-knight/` likewise is a Warner Bros homepage. The real
  routes are `/details/movie|tv/<tmdb_id>` (from `sitemap.xml`).
- **Resolution:** used sitemap + DOM navigation as ground truth.
- **[→ main] Lesson:** field names like `homepage`, `imdb_id`, and
  Netflix-style ids regularly masquerade as site routes. Verify every
  candidate route with a real HTTP request before filling `SiteConfig`
  (add to the `AGENT_GUIDE.md` validation checklist).

## I-4. Empty selector → `document.querySelector('')` throws (fork bug)

- **Symptom:** with `PREV_EPISODE_SELECTOR=""` (movies have no prev/next
  buttons), `WatchActivity.pageClick` injected
  `document.querySelector('')`, which throws a SyntaxError and the
  click silently did nothing (no toast, JS callback never fires).
  Also `EPISODE_*` fetch injected an invalid selector.
- **Cause:** `docs/API.md` promises "an empty selector means disabled",
  but `WatchActivity` never implemented that.
- **Resolution (fork, generic fix):** guard in `pageClick`,
  `toggleAutoplay`, `fetchEpisodesFromPage`, `selectEpisode`:
  empty selector → toast *"not available for this site"* and return.
  Episode picker button is hidden when `EPISODE_LIST_SELECTOR` is empty.
- **[→ main] Backport candidate (code):** this is a harness bug, not a
  fork detail. Upstream the guards.

## I-5. Play button has no stable hook; focus must target Tailwind classes

- **Symptom:** harness auto-focus fell back to `iframe, video` (nothing
  to focus pre-Play); D-pad users had no way to trigger Play without a
  mouse.
- **Cause:** the Play button is `<button class="bg-white text-black
  px-9 ..."><span>Play</span></button>` — no id/aria-label; its label
  varies (`Play` / `Resume` / `Play in 4K`), so text matching is worse.
- **Resolution (fork):** `PLAYER_FOCUS_SELECTOR = "button.bg-white.text-black"`.
- **[→ main] Trick:** for SPA sites, DOM-browser inspection (class
  chains on the target) beats reading minified chunks; document the
  "focus = most specific stable class combo" rule in `AGENT_GUIDE.md`.

## I-6. `window.open` popup hijack fires on first click

- **Symptom:** browser console + network showed the site's ad script
  wrapping `window.open` and attempting a popup-form submission on the
  first mousedown; the harness `window.open = () => null` stub (and
  `onCreateWindow=false`) is load-bearing — without it, the first OK
  tap on the player would fire a popup.
- **Resolution (harness already had it):** verified the PLAYER_PAGE_SCRIPT
  stub still applies on this site; did **not** need to change anything.
  Noted because future sites in this family (same ad script) will too.
- **[→ main] Best practice:** always check what the site does to
  `window.open` / first click before assuming WebView popups are off —
  add to the agent checklist.

## I-7. Ad-trackers + Cloudflare noise, but harmless

- **Symptom:** console errors `ERR_TUNNEL_CONNECTION_FAILED` for
  `*.cfd` / `*.rest` ad pings and `static.cloudflareinsights.com`
  beacon; `GET /api/watchlist → 401` (anonymous user).
- **Cause:** blocklist domains unreachable from the test network; the
  401 is expected (watchlist needs an account, which the harness is
  documented not to support).
- **Resolution:** none needed; cataloguing it so future agents don't
  chase it.

## I-8. TV seasons/episodes are not remote-controllable (limitation)

- **Symptom:** TV Play defaults to S1E1 (or local resume state).
  Switching season requires the site's `<select>`; picking an episode
  requires clicking its card. The harness has no seam for either.
- **Resolution (fork, v1):** accepted limitation — the episode picker
  stays disabled for this site (`EPISODE_LIST_SELECTOR=""`), and TV
  playback starts at the site default. Documented in `SiteConfig` and
  README of this fork.
- **[→ main] Future seam (Phase 4):** a generic "site control" hook
  (`CONTROL_SELECTOR` + `CONTROL_VALUE`) would let a config drive
  `<select>`-based season pickers without fork code. Not built here.

## I-9. Same-account GitHub forks are impossible (workflow note)

- **Symptom:** `gh repo fork DigitalniVuk/tv-harness` → *"A single user
  account cannot own both a parent and fork."*
- **Resolution:** site repos are seeded as **copies** of `tv-harness`
  `main` (`tv-harness-67movies`), with the seed commit referencing the
  upstream SHA. They are not GitHub-network forks.
- **[→ main] Policy:** document this in `HARNESS_PLAN.md` — upstream
  improvements come back via cherry-pick/merge from the seeded SHA,
  never by merging site code into `tv-harness`.

## I-10. Top bar overflows with 6+ tabs (brand squeezed out)

- **Symptom:** with 6 tabs + REFRESH, the top bar ran out of room — the
  `DISPLAY_NAME` label was squeezed to zero width, so the app looked
  unbranded (tabs only).
- **Cause:** tab buttons are fixed-width (`dp(128)`, 12sp text) and the
  top bar is a `LinearLayout` with no scrolling or shrinking. Harmless
  for ≤4 tabs, broken for 6+.
- **Resolution (fork, v1):** accepted cosmetic issue — all tabs still
  work; brand loss noted here.
- **[→ main] Upstream fix:** make the tab row horizontally scrollable
  (or shrink tabs with the count) in `CatalogActivity`.

## I-11. Harness OSD captures D-pad centre, so site Play is unreachable

- **Symptom:** on the details modal, pressing OK (DPAD_CENTER) did
  nothing — the site's Play button never received the click.
- **Cause:** `WatchActivity.dispatchKeyEvent` consumes every D-pad key
  while the OSD is hidden (it re-shows the OSD and focuses the first
  OSD button). A site's own buttons can only be driven via the OSD
  *actions* (pause/prev/next/autoplay). The harness was designed for
  sites whose player is operated through those seams.
- **Resolution (fork):** the OSD's PAUSE / PLAY button now clicks the
  site's Play button (`PAUSE_TOGGLE_SELECTOR` = Play selector), which
  starts the embed. Once the iframe is up, its own UI plays.
- **[→ main] Workaround pattern (document):** for modal-detail sites,
  point `PAUSE_TOGGLE_SELECTOR` at the site's start button. A cleaner
  upstream seam would be a dedicated "play" OSD button; backport the
  docs first.

## I-12. Harness focus timer (15 s) expired before SPA details rendered

- **Symptom:** even when focus could be set, the focus script had
  already given up by the time the details modal (client-fetched via
  TMDB) appeared ~30 s later on the Fire TV.
- **Cause:** `PLAYER_PAGE_SCRIPT` retried the focus target 30×500 ms
  (15 s) from `onPageFinished`; SPA data arrives long after.
- **Resolution (fork, generic fix):** retry up to 480×500 ms (~4 min)
  and `scrollIntoView({block:'center'})` before focusing (the modal
  scrolls; Play is below the backdrop fold).
- **[→ main] Backport candidate (code):** this is a harness bug for any
  SPA-backed site. Upstream the longer window + scroll.

## I-13. VidQueen media API returns `source: null` from this network

- **Symptom:** the embed player loads (UI, server card, PG-13 splash)
  but the video area stays black indefinitely on both the TV and a
  desktop browser.
- **Cause:** `https://api.vidqueen.st/movie?id=155&mode=json`
  (all sources, all 4 tested movies + TV) returns
  `{"source":null}` from this network — the media server has nothing
  to serve here (geo/IP gating on their side). Not a WebView or
  harness failure; the same happens in a desktop browser.
- **Resolution:** none available in the harness. Verified everything
  up to the video element: embed loads, correct URL, correct server.
  Needs a re-test from a network where the server serves a source.
- **[→ main] Lesson:** when a player stalls black, check the media API
  response first (desktop + curl) before blaming the WebView.

## I-14. Fork edits landed in the main repo (relative paths) — recovered

- **Symptom:** after "rebuilding", the installed app still showed the
  placeholder harness (example.com tabs, "TV Harness" brand).
- **Cause:** `write`/`edit` paths were repo-relative to the session
  workspace (`TV Play App`, the main harness), not to the fork dir.
  The scraper, config, and WatchActivity edits had all gone into main.
- **Resolution:** `git checkout` restored the three main files
  (verified `git status` clean) and the docs were deleted there;
  fork edits were re-applied with absolute paths, then rebuilt.
- **[→ main] Process note:** agents working in a fork dir must resolve
  **absolute paths** before writing. Cheap check after every fork
  edit: `git -C <main> status` must be clean.
