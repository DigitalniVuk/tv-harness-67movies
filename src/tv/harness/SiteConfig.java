package tv.harness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Single plug-in point for the harness.
 *
 * <p><b>Site: 67movies.st</b> (fork of {@code tv-harness} for this one
 * site, see {@code docs/INVESTIGATION.md} and {@code docs/ISSUES.md}).
 * An agent pointing this fork at another site edits ONLY this file.
 *
 * <p>67movies is a Next.js site whose catalogue rows live in React
 * Server Component JSON embedded in the home page HTML (there are no
 * server-rendered card divs), so {@link CatalogScraper} uses its JSON
 * mode for this fork (see its docs).
 */
public final class SiteConfig {

    /** Shown in the top bar. */
    public static final String DISPLAY_NAME = "67movies";

    /** Origin of the website, no trailing slash, https only. */
    public static final String BASE_URL = "https://67movies.st";

    /** Home page inside the player WebView (used when playback has no URL). */
    public static final String HOME_PATH = "/";

    /** TV browsers are unrecognised by many sites; present a mobile Android UA. */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; AFTSS) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";

    // ------------------------------------------------------------------
    // Catalogue tabs (the "endpoints" the app must fill)
    //
    // Each Tab is fetched with GET BASE_URL + path. This fork's scraper
    // reads the ?rail=<key> query value to pick which home-page rail to
    // extract (see CatalogScraper). The first tab is the landing view
    // (HOME). The REFRESH button re-fetches all.
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
            new Tab("home", "HOME", "/?rail=trending-movies"),
            new Tab("series", "SERIES", "/?rail=trending-series"),
            new Tab("popular", "POPULAR", "/?rail=popular-tmdb"),
            new Tab("top", "TOP", "/?rail=top-rated-movies"),
            new Tab("action", "ACTION", "/?rail=action-movies"),
            new Tab("horror", "HORROR", "/?rail=horror-movies")
    ));

    // ------------------------------------------------------------------
    // Category discovery (the "genres" tab)
    //
    // 67movies has no category/listing pages (only "/" and an "/anime"
    // placeholder), so there is no genres tab in TABS above. These
    // values point at a page with no matching anchors as a safe no-op.
    // ------------------------------------------------------------------

    public static final String CATEGORY_DISCOVERY_PATH = "/";
    public static final String CATEGORY_PATH_PREFIX = "/__no-categories__/";

    // ------------------------------------------------------------------
    // Item parsing (one rule set per site, applied to every listing page)
    //
    // Not used by this fork's JSON mode (kept for the HTML fallback in
    // CatalogScraper); item URL/poster/details are built from the JSON
    // fields documented there.
    // ------------------------------------------------------------------

    /** CSS class marking one item block in the listing HTML. */
    public static final String ITEM_CLASS = "item";

    /** Item links contain this path fragment, e.g. "/watch/". */
    public static final String ITEM_LINK_PATH = "/details/";

    /** Anchor classes that hold the item title (comma separated). */
    public static final String ITEM_TITLE_CLASSES = "name,d-title";

    /** Regex matched against the item's HTML; group 1 is the type label. */
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
    // The details page is a modal overlay (.dm-shell). Clicking the
    // site's own Play button loads the third-party embed player in an
    // iframe (.pl-frame, player.vidqueen.st). All selectors are
    // optional: leaving one empty disables that control.
    // ------------------------------------------------------------------

    /** CSS selectors to hide (footer only; the modal overlays everything). */
    public static final String PLAYER_HIDE_CSS = "footer";

    /** CSS selectors that must stay visible. Empty: don't fight the site. */
    public static final String PLAYER_KEEP_SELECTORS = "";

    /** Main player element; harness centres it in 16:9. Empty: leave as-is. */
    public static final String PLAYER_ELEMENT_SELECTOR = "";

    /** Player iframe/video element. Empty: leave the site's layout alone. */
    public static final String PLAYER_FRAME_SELECTOR = "";

    /**
     * First element the harness focuses on page load: the details modal's
     * Play button. It has no id or aria-label and its label varies
     * ("Play" / "Resume" / "Play in 4K"), so the stable hook is its
     * Tailwind class pair (see docs/ISSUES.md I-5).
     */
    public static final String PLAYER_FOCUS_SELECTOR = "button.bg-white.text-black";

    /**
     * Episode/section controls. Movies have none; TV seasons are picked
     * in the site's own <select> (see docs/ISSUES.md I-8), so this fork
     * keeps the picker disabled (empty = disabled, the OSD button hides).
     */
    public static final String EPISODE_LIST_SELECTOR = "";
    public static final String EPISODE_ACTIVE_SELECTOR = "";
    public static final String EPISODE_NUM_ATTR = "data-num";

    /** Previous/next episode buttons. */
    public static final String PREV_EPISODE_SELECTOR = "";
    public static final String NEXT_EPISODE_SELECTOR = "";

    /** Autoplay toggle (its state also lives in localStorage auto_play). */
    public static final String AUTOPLAY_SELECTOR = "";

    /**
     * Click target that toggles play/pause; empty = dispatch Space key.
     * 67movies has no pause control, so this points at the details modal's
     * Play button: the harness OSD's PAUSE / PLAY button is how a remote
     * user starts playback (see docs/ISSUES.md I-11). Once the embed
     * iframe is up, its own controls handle pause/seek.
     */
    public static final String PAUSE_TOGGLE_SELECTOR = "button.bg-white.text-black";

    // ------------------------------------------------------------------
    // Direct-embed mode + stuck-media fallback (harness extension)
    //
    // The details page plays in a cross-origin iframe
    // (player.vidqueen.st), whose controls page JS cannot drive. When
    // the harness spots that iframe it navigates the WebView straight
    // to the iframe's URL; every control then lives in the top document.
    // A watchdog ticks the embed's own loading state ("Try another
    // server" + video progress) and cycles servers if it is stuck, and
    // unmutes once playback starts (embed players start muted to
    // satisfy autoplay policies). Null = disabled.
    // ------------------------------------------------------------------

    /** Substring matched against the current URL for "is the embed page?". */
    public static final String DIRECT_EMBED_HOST = "player.vidqueen.st";

    /** Iframe host fragment looked for on the details page. */
    public static final String EMBED_IFRAME_HOST = DIRECT_EMBED_HOST;

    /** Play/pause control on the direct embed page. */
    public static final String EMBED_PLAY_SELECTOR = "button[title='Play / pause']";

    /** "Try another server" button; visible while the load is stuck. */
    public static final String STALL_FALLBACK_SELECTOR = ".vl-load-alt";

    /** First stall click after this long (ms) with no video progress. */
    public static final long STALL_FIRST_TIMEOUT_MS = 20000L;

    /** Delay between automatic server switches (ms). */
    public static final long STALL_RETRY_MS = 12000L;

    /** Automatic server switches per page load. */
    public static final int STALL_MAX_CLICKS = 10;

    /** Volume (mute/unmute) control on the embed page. */
    public static final String SOUND_CONTROL_SELECTOR = "button.volume-icon";

    // ------------------------------------------------------------------
    // Host allowlists (also feed AdBlocker)
    // ------------------------------------------------------------------

    /**
     * Hosts belonging to the site. Never ad-blocked, and their URLs are
     * allowed through the player. The details page fetches TMDB
     * client-side, posters come from TMDB, and playback is a separate
     * embed host — all four must be here or those break.
     */
    public static final String[] FIRST_PARTY_HOSTS = {
            "67movies.st", "www.67movies.st",
            "api.themoviedb.org", "image.tmdb.org",
            "player.vidqueen.st",
            "www.youtube.com", "i.ytimg.com"
    };

    /** Extra never-blocked hosts (media API + subtitle thumbnails). */
    public static final String[] FIRST_PARTY_MEDIA_HOSTS = {
            "api.vidqueen.st", "eruzei.com"
    };

    /**
     * Hosts poster images may be loaded from. Posters are constructed
     * from TMDB poster paths, so only the TMDB image host is needed.
     */
    public static final String[] ALLOWED_IMAGE_PREFIXES = {
            "https://image.tmdb.org/"
    };

    private SiteConfig() {}
}
