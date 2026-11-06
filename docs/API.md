# API — harness endpoints and contracts

This document has three parts: the **website endpoints** the harness needs,
the **Java API** inside the harness, and the **WebView JS contract** the
player uses to drive the site's own player. All three are filled in from
`SiteConfig.java` only.

## 1. Website endpoints to fill

Every request is `GET BASE_URL + path` with no-cache headers and a unique
`__tv_refresh` query parameter (see `CatalogScraper.fetchHtml`). The site
must return server-rendered HTML (scripts are never run for the catalogue).

### Catalogue

| Endpoint | Config key | Purpose | Required? |
|---|---|---|---|
| `GET HOME_PATH` | `HOME_PATH` (default `/home`) | Player fallback page; also the `Referer` of catalogue fetches | Yes, point at anything real |
| `GET Tab.path` (× N) | `TABS[*].path` | One listing page per tab (recent, top, …). Tabs with `path == null` are rendered but not fetched; keep one `path == null` tab for the category grid | Yes, ≥ 1 tab with a path |
| `GET CATEGORY_DISCOVERY_PATH` | `CATEGORY_DISCOVERY_PATH` | Page listing category links (usually the home page) | Only if categories are wanted |
| `GET CATEGORY_PATH_PREFIX + slug` | `CATEGORY_PATH_PREFIX` | One category's listing page (parse with the item rules below) | Only if categories are wanted |

Category links are discovered by scanning anchors on the discovery page:
an anchor whose `href` starts with `CATEGORY_PATH_PREFIX` yields a slug
(everything up to `?`/`#`, no percent-encoding) and a display name (anchor
text, falling back to the slug with dashes as spaces).

### Item extraction (applies to every listing page)

For one listing page's HTML, the harness extracts items using these rules,
all from `SiteConfig`:

1. Split the HTML into blocks: each `<div>` whose class list contains the
   whole word `ITEM_CLASS` (matched with balanced `<div>` depth).
2. In a block, the item URL is the first `<a href="...">` containing
   `ITEM_LINK_PATH`; relative URLs are resolved against `BASE_URL`.
3. The title is the anchor whose class contains one of the comma-separated
   `ITEM_TITLE_CLASSES` (fallback: the URL anchor's own text).
4. The poster is the first `<img>`'s `data-src` (fallback `src`), resolved
   against `BASE_URL`. Posters load only if the URL starts with one of
   `ALLOWED_IMAGE_PREFIXES`.
5. Details text is built from three optional regexes, each match's group 1
   appended after a middle dot: `ITEM_TYPE_REGEX` (default label
   `ITEM_DEFAULT_TYPE` when absent), `ITEM_RATING_REGEX`,
   `ITEM_EPISODES_REGEX` (rendered as `Ep <text>`). An empty regex disables
   that fragment.

A block with no URL or no title is skipped silently.

### Player page

The player WebView loads the item URL (`BASE_URL + item path`); anything
not starting with `BASE_URL + "/"` falls back to `HOME_URL`. The page must
be loadable in a WebView with JavaScript enabled, DOM storage enabled, and
media playback without user gesture. First-party hosts in
`FIRST_PARTY_HOSTS` are never ad-blocked; everything else goes through
`AdBlocker` (media file extensions such as `.m3u8`, `.mp4`, `.mpd`,
`.ts`, `.vtt`, … always pass).

## 2. Java API (internal, site-agnostic)

Signature overview. Anything not listed here does not exist.

```java
// All config lives in SiteConfig constants.
SiteConfig.BASE_URL             // String, https origin, no trailing slash
SiteConfig.DISPLAY_NAME         // String, top-bar brand
SiteConfig.HOME_PATH            // String, e.g. "/home"
SiteConfig.USER_AGENT           // String, mobile Android UA
SiteConfig.TABS                 // List<Tab> {id, label, path|null}
SiteConfig.CATEGORY_DISCOVERY_PATH   // String
SiteConfig.CATEGORY_PATH_PREFIX      // String, e.g. "/genre/"
SiteConfig.ITEM_CLASS                // String, whole-word class on item blocks
SiteConfig.ITEM_LINK_PATH            // String, substring of item hrefs
SiteConfig.ITEM_TITLE_CLASSES        // String, comma-separated anchor classes
SiteConfig.ITEM_TYPE_REGEX           // String regex | "" to disable
SiteConfig.ITEM_RATING_REGEX         // String regex | "" to disable
SiteConfig.ITEM_EPISODES_REGEX       // String regex | "" to disable
SiteConfig.ITEM_DEFAULT_TYPE         // String fallback details label
SiteConfig.PLAYER_HIDE_CSS           // String CSS selector list to hide
SiteConfig.PLAYER_KEEP_SELECTORS     // String CSS selector list to force visible
SiteConfig.PLAYER_ELEMENT_SELECTOR   // String, main player container
SiteConfig.PLAYER_FRAME_SELECTOR     // String, iframe/video inside it
SiteConfig.PLAYER_FOCUS_SELECTOR     // String, first element to focus on load
SiteConfig.EPISODE_LIST_SELECTOR     // String, selector list of episode anchors
SiteConfig.EPISODE_ACTIVE_SELECTOR   // String, current episode anchor
SiteConfig.EPISODE_NUM_ATTR          // String, attribute with the episode number
SiteConfig.PREV_EPISODE_SELECTOR     // String
SiteConfig.NEXT_EPISODE_SELECTOR     // String
SiteConfig.AUTOPLAY_SELECTOR         // String | "" to disable autoplay control
SiteConfig.PAUSE_TOGGLE_SELECTOR     // String | "" to use the Space-key bridge
SiteConfig.FIRST_PARTY_HOSTS         // String[], never ad-blocked
SiteConfig.ALLOWED_IMAGE_PREFIXES    // String[], poster URL prefixes

// CatalogScraper (all static, throws Exception on HTTP/parse failure)
CatalogScraper.fetchItems(path)       // List<CatalogItem> for one listing page
CatalogScraper.fetchCategories()      // List<CatalogCategory> from discovery page

// CatalogItem {title, watchUrl, posterUrl, details}
// CatalogCategory {title, path}

// AdBlocker
AdBlocker.blocks(rawUrl)              // boolean: true = serve empty 204 response
```

`CatalogActivity` tab handling contract: tab `id` values are compared as
strings against `"home"` (show every fetched tab as a row plus a category
strip) and `"genres"` (menu label of the category-grid tab), and
`"category"` is the internal id used when a category page is open. Keep
those three ids exactly; other ids are free.

## 3. WebView JS contract (player)

`WatchActivity` drives the site's player only through these operations.
Selectors come from `SiteConfig` (shown in `<>`). An empty selector means
"that control is disabled".

| Operation | JS performed |
|---|---|
| Customise page | Inject a `<style>` element: hide `<>PLAYER_HIDE_CSS`, force-expand `<>PLAYER_KEEP_SELECTORS`, centre `<>PLAYER_ELEMENT_SELECTOR` in 16:9, stretch `<>PLAYER_FRAME_SELECTOR` to fill it. Replaces `window.open`/`alert` with no-ops. |
| Initial focus | Every 500 ms (max 15 s): focus `<>PLAYER_FOCUS_SELECTOR` if visible, else the first `<>PLAYER_FRAME_SELECTOR`; sets `tabindex="0"` first. |
| List episodes | `document.querySelectorAll(<EPISODE_LIST_SELECTOR>)`; number = `a[EPISODE_NUM_ATTR]` or text, name = `title` attr or text; active = `<>EPISODE_ACTIVE_SELECTOR`. Returns `{current, episodes[]}` as JSON. |
| Select episode | Click the anchor from `<>EPISODE_LIST_SELECTOR` whose number (same rule) equals the picked number. |
| Previous / next | `document.querySelector(<PREV_/NEXT_EPISODE_SELECTOR>).click()` |
| Pause / play | Click `<>PAUSE_TOGGLE_SELECTOR` if set; otherwise dispatch a `keydown` event with `keyCode`/`which` = 32 (Space) on `window`. |
| Autoplay | Click `<>AUTOPLAY_SELECTOR`; state = `localStorage["auto_play"]` (`"1"`/`"true"`), else class `active`/`on`, `data-value="1"`, or `aria-checked`. |
| Block popups | `onCreateWindow` returns false; `window.open` stubbed; `alert`/`confirm` cancelled. Main-frame navigations and subresource requests matching `AdBlocker` return an empty 204. |

Error surfacing: a failed selector click shows a toast ("… not available
yet") and never crashes the player. Missing episodes show "Episodes are
still loading from source…" and keep the picker usable on later loads.
