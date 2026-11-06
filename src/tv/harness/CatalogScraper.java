package tv.harness;

import android.text.Html;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches only the site's HTML catalogue pages; it never runs the site's
 * scripts. All site-specific values come from {@link SiteConfig};
 * this class is site-agnostic and must not gain any.
 */
final class CatalogScraper {
    private static final Pattern DIV = Pattern.compile("(?is)<(/?)div\\b([^>]*)>");
    private static final Pattern ATTR = Pattern.compile(
            "(?is)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern WATCH_ANCHOR = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a\\s*>");
    private static final Pattern IMG = Pattern.compile("(?is)<img\\b([^>]*)>");
    private static final Pattern ITEM_CLASS = Pattern.compile(
            "(?is)\\bclass\\s*=\\s*(['\"])[^'\"]*\\b" + Pattern.quote(SiteConfig.ITEM_CLASS) + "\\b[^'\"]*\\1");
    private static final Pattern ITEM_TYPE = compile(SiteConfig.ITEM_TYPE_REGEX);
    private static final Pattern ITEM_RATING = compile(SiteConfig.ITEM_RATING_REGEX);
    private static final Pattern ITEM_EPISODES = compile(SiteConfig.ITEM_EPISODES_REGEX);

    private static Pattern compile(String regex) {
        return regex == null || regex.isEmpty() ? null : Pattern.compile(regex);
    }

    private CatalogScraper() {}

    /** Fetches and parses one listing page (SiteConfig tab or category path). */
    static List<CatalogItem> fetchItems(String path) throws Exception {
        String html = fetchHtml(path);
        List<CatalogItem> items = new ArrayList<>();
        for (String block : itemBlocks(html)) {
            CatalogItem item = parseItem(block);
            if (item != null) items.add(item);
        }
        return items;
    }

    /** Discovers category links on the site's discovery page. */
    static List<CatalogCategory> fetchCategories() throws Exception {
        String html = fetchHtml(SiteConfig.CATEGORY_DISCOVERY_PATH);
        LinkedHashMap<String, CatalogCategory> categories = new LinkedHashMap<>();
        Matcher links = WATCH_ANCHOR.matcher(html);
        while (links.find()) {
            Map<String, String> attrs = attributes(links.group(1));
            String href = attrs.get("href");
            if (href == null) continue;
            String path = href;
            if (href.startsWith(SiteConfig.BASE_URL)) path = href.substring(SiteConfig.BASE_URL.length());
            if (!path.startsWith(SiteConfig.CATEGORY_PATH_PREFIX)) continue;
            String slug = path.substring(SiteConfig.CATEGORY_PATH_PREFIX.length()).split("[/?#]", 2)[0];
            if (slug.isEmpty() || slug.contains("%")) continue;
            String name = plainText(links.group(2));
            if (name.isEmpty()) name = slug.replace('-', ' ');
            categories.put(slug, new CatalogCategory(name, SiteConfig.CATEGORY_PATH_PREFIX + slug));
        }
        return new ArrayList<>(categories.values());
    }

    private static String fetchHtml(String path) throws Exception {
        String requestPath = path;
        int query = path.indexOf('?');
        String separator = query < 0 ? "?" : "&";
        requestPath += separator + "__tv_refresh=" + System.nanoTime();
        URL url = new URL(path.startsWith("http") ? requestPath : SiteConfig.BASE_URL + requestPath);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", SiteConfig.USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Referer", SiteConfig.BASE_URL + SiteConfig.HOME_PATH);
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        connection.setRequestProperty("Pragma", "no-cache");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Site returned HTTP " + code);
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                return new String(out.toByteArray(), Charset.forName("UTF-8"));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static List<String> itemBlocks(String html) {
        List<String> out = new ArrayList<>();
        Matcher tags = DIV.matcher(html);
        int start = -1, depth = 0;
        while (tags.find()) {
            boolean close = !tags.group(1).isEmpty();
            if (start < 0) {
                if (!close && ITEM_CLASS.matcher(tags.group(2)).find()) {
                    start = tags.start();
                    depth = 1;
                }
                continue;
            }
            if (close) depth--;
            else depth++;
            if (depth == 0) {
                out.add(html.substring(start, tags.end()));
                start = -1;
            }
        }
        return out;
    }

    private static CatalogItem parseItem(String block) {
        Matcher anchors = WATCH_ANCHOR.matcher(block);
        String watchUrl = null, title = null;
        while (anchors.find()) {
            Map<String, String> attrs = attributes(anchors.group(1));
            String href = attrs.get("href");
            if (href == null || !href.contains(SiteConfig.ITEM_LINK_PATH)) continue;
            if (watchUrl == null) watchUrl = absolute(href);
            String classes = attrs.get("class");
            if (classes != null && hasTitleClass(classes)) {
                title = plainText(anchors.group(2));
                break;
            }
            if (title == null) title = plainText(anchors.group(2));
        }
        if (watchUrl == null || title == null || title.isEmpty()) return null;

        String poster = null;
        Matcher images = IMG.matcher(block);
        if (images.find()) {
            Map<String, String> attrs = attributes(images.group(1));
            poster = attrs.get("data-src");
            if (poster == null || poster.isEmpty()) poster = attrs.get("src");
            poster = poster == null ? null : absolute(poster);
        }

        String details = SiteConfig.ITEM_DEFAULT_TYPE;
        if (ITEM_TYPE != null) {
            Matcher type = ITEM_TYPE.matcher(block);
            if (type.find()) details = plainText(type.group(1));
        }
        if (ITEM_RATING != null) {
            Matcher rating = ITEM_RATING.matcher(block);
            if (rating.find()) details += "  ·  " + plainText(rating.group(1));
        }
        if (ITEM_EPISODES != null) {
            Matcher episodes = ITEM_EPISODES.matcher(block);
            if (episodes.find()) details += "  ·  Ep " + plainText(episodes.group(1));
        }
        return new CatalogItem(title, watchUrl, poster, details);
    }

    private static boolean hasTitleClass(String classes) {
        for (String token : SiteConfig.ITEM_TITLE_CLASSES.split(",")) {
            if (classes.contains(token.trim())) return true;
        }
        return false;
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher matcher = ATTR.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            out.put(matcher.group(1).toLowerCase(Locale.ROOT), value);
        }
        return out;
    }

    private static String absolute(String value) {
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return SiteConfig.BASE_URL + value;
        return value;
    }

    private static String plainText(String html) {
        String text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
        return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
