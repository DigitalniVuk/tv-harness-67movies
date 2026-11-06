package tv.harness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Single plug-in point for the harness.
 *
 * <p>To point this app at any website, an agent edits ONLY this file
 * (see docs/AGENT_GUIDE.md). Every other class reads its site-specific
 * values from here; there is no site-specific code anywhere else.
 *
 * <p>Values below are TODO placeholders, not a real site. Nothing in the
 * app will work until they are filled in. Keep the placeholder host
 * (example.com) in the repo so no real site is endorsed by the template.
 */
public final class SiteConfig {

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /** Shown in the top bar. Replace with the site's public name. */
    public static final String DISPLAY_NAME = "TV Harness";

    /**
     * Origin of the website, no trailing slash, https only.
     * Used for catalogue fetch, Referer, relative URL resolution, and
     * validating URLs opened in the player.
     *
     * TODO: e.g. "https://www.example.com"
     */
    public static final String BASE_URL = "https://example.com";

    /** Home page inside the player WebView (used when playback has no URL). */
    public static final String HOME_PATH = "/home";

    /**
     * Browsers rarely identify as a TV, so the harness presents a mobile
     * Android UA. Change only if the site rejects it.
     */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; AFTSS) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";

    // ------------------------------------------------------------------
    // Catalogue tabs (the "endpoints" the app must fill)
    //
    // Each Tab is fetched with GET BASE_URL + path. The first tab is
    // also the landing view (HOME). The REFRESH button re-fetches all.
    // TODO: point each path at a real listing page of the target site,
    // or remove extra tabs from CatalogActivity (TABS array there).
    // ------------------------------------------------------------------

    public static final class Tab {
        final String id;
        final String label;
        final String path;

        private Tab(String id, String label, String path) {
            this.id = id;
            this.label = label;
            this.path = path;
        }
    }

    /** Landing view: a row of tab panels rendered from these paths. */
    public static final List<Tab> TABS = Collections.unmodifiableList(Arrays.asList(
            new Tab("home", "HOME", "/latest-updated"),
            new Tab("updated", "RECENT", "/latest-updated"),
            new Tab("popular", "TOP", "/most-viewed"),
            new Tab("genres", "GENRES", null)
    ));

    // ------------------------------------------------------------------
    // Category discovery (the "genres" tab)
    //
    // TODO: set CATEGORY_DISCOVERY_PATH to a page that lists categories
    // (usually the home page), and CATEGORY_PATH_PREFIX to the URL prefix
    // those links use, e.g. "/genre/". Category pages are fetched with
    // GET BASE_URL + prefix + slug and parsed with the item rules below.
    // ------------------------------------------------------------------

    public static final String CATEGORY_DISCOVERY_PATH = "/home";
    public static final String CATEGORY_PATH_PREFIX = "/genre/";

    // ------------------------------------------------------------------
    // Item parsing (one rule set per site, applied to every listing page)
    //
    // The scraper finds blocks whose opening <div> has ITEM_CLASS, then
    // extracts: the first link containing ITEM_LINK_PATH (the item URL),
    // the anchor whose class contains one of ITEM_TITLE_CLASSES (title),
    // the first <img data-src|src> (poster), and the details selectors.
    //
    // TODO: fill each pattern from the real site's HTML (see API.md).
    // ------------------------------------------------------------------

    /** CSS class marking one item block in the listing HTML. */
    public static final String ITEM_CLASS = "item";

    /** Item links contain this path fragment, e.g. "/watch/". */
    public static final String ITEM_LINK_PATH = "/watch/";

    /** Anchor classes that hold the item title (comma separated). */
    public static final String ITEM_TITLE_CLASSES = "name,d-title";

    /**
     * Regex matched against the item's HTML; group 1 is the type label
     * (e.g. "TV", "Movie"). Empty pattern = no type label.
     *
     * TODO: rewrite against the real site's markup, or use "".
     */
    public static final String ITEM_TYPE_REGEX =
            "(?is)<div[^>]*class=[\"'][^\"']*right[^\"']*[\"'][^>]*>(.*?)</div>";

    /** Regex for the rating text (shown after a middle dot). */
    public static final String ITEM_RATING_REGEX =
            "(?is)class=[\"'][^\"']*rated[^\"']*[\"'][^>]*>.*?<span[^>]*>(.*?)</span>";

    /** Regex for the episode/status count text (shown after "Ep "). */
    public static final String ITEM_EPISODES_REGEX =
            "(?is)class=[\"'][^\"']*ep-status sub[^\"']*[\"'][^>]*>.*?<span[^>]*>(.*?)</span>";

    /** Fallback label when ITEM_TYPE_REGEX does not match. */
    public static final String ITEM_DEFAULT_TYPE = "Item";

    // ------------------------------------------------------------------
    // Player (WebView playback screen)
    //
    // The player loads BASE_URL + the item URL, hides chrome with
    // PLAYER_HIDE_CSS, and drives the site's own player through the
    // selectors below. All selectors are optional: leaving one empty
    // disables that control instead of breaking the player.
    // TODO: fill from the real player page (see API.md).
    // ------------------------------------------------------------------

    /** CSS selectors to hide (site navigation, footers, comments…). */
    public static final String PLAYER_HIDE_CSS =
            "body>header,header,#footer,.footer,.sidebar,.breadcrumb,"
            + ".share-buttons,.comment,.comments";

    /** CSS selectors that must stay visible and fill the player area. */
    public static final String PLAYER_KEEP_SELECTORS =
            "body,#watch-main,.watch-container,.aside-wrapper,aside.main,"
            + "#w-media,#w-player";

    /** Main player element; harness centres it in 16:9. */
    public static final String PLAYER_ELEMENT_SELECTOR = "#w-player";

    /** Player iframe/video element inside PLAYER_ELEMENT_SELECTOR. */
    public static final String PLAYER_FRAME_SELECTOR = "#player iframe,#player video";

    /** First element the harness focuses on page load (e.g. play button). */
    public static final String PLAYER_FOCUS_SELECTOR = "#player .play";

    /** Episode list container; episodes are its <a> children. */
    public static final String EPISODE_LIST_SELECTOR = "#w-episodes .episodes a, #w-episodes a[data-num]";

    /** Currently-playing episode (inside EPISODE_LIST_SELECTOR). */
    public static final String EPISODE_ACTIVE_SELECTOR = "#w-episodes a.active";

    /** Attribute holding the episode number (fallbacks to text content). */
    public static final String EPISODE_NUM_ATTR = "data-num";

    /** Previous/next episode buttons. */
    public static final String PREV_EPISODE_SELECTOR = ".ctrl.prev";
    public static final String NEXT_EPISODE_SELECTOR = ".ctrl.next";

    /** Autoplay toggle (its state also lives in localStorage auto_play). */
    public static final String AUTOPLAY_SELECTOR = "#controls .auto-play";

    /** Click target that toggles play/pause; empty = dispatch Space key. */
    public static final String PAUSE_TOGGLE_SELECTOR = "";

    // ------------------------------------------------------------------
    // Host allowlists (also feed AdBlocker)
    // ------------------------------------------------------------------

    /**
     * Hosts belonging to the site. These are never ad-blocked, and their
     * URLs are allowed through the player. Include every media/CDN host
     * the site uses, or playback will break.
     *
     * TODO: e.g. {"example.com", "www.example.com", "cdn.example.com"}
     */
    public static final String[] FIRST_PARTY_HOSTS = {
            "example.com", "www.example.com"
    };

    /**
     * Hosts poster images may be loaded from. Posters from other hosts
     * are silently skipped (blocking keeps the UI fast and private).
     *
     * TODO: e.g. {"https://cdn.example.com/"}
     */
    public static final String[] ALLOWED_IMAGE_PREFIXES = {
            "https://cdn.example.com/"
    };

    private SiteConfig() {}
}
