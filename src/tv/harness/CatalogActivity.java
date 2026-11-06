package tv.harness;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, remote-first catalog. HTML is parsed as data; only playback opens WebView. */
public final class CatalogActivity extends Activity {
    private static final int BG = Color.rgb(10, 12, 18);
    private static final int PANEL = Color.rgb(20, 25, 36);
    private static final int CARD = Color.rgb(26, 31, 43);
    private static final int ACCENT = Color.rgb(126, 91, 242);
    private static final int FOCUS = Color.rgb(83, 184, 255);
    private static final int WHITE = Color.rgb(244, 246, 250);
    private static final int MUTED = Color.rgb(169, 180, 198);
    private static final int MAX_CARDS = 40;

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService imageWorkers = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(10 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };
    private final Set<String> loadingImages = Collections.synchronizedSet(new HashSet<>());
    // Items keyed by SiteConfig tab id; category grid is fetched separately.
    private final java.util.Map<String, List<CatalogItem>> tabItems = new java.util.LinkedHashMap<>();
    private final List<CatalogItem> categoryItems = new ArrayList<>();
    private final List<CatalogCategory> genres = new ArrayList<>();
    private LinearLayout content;
    private TextView status;
    private View firstContentFocus;
    private final List<TextView> navTabs = new ArrayList<>();
    private String mode = "home";
    private String activeCategory;
    private int generation;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        createScreen();
        loadCatalog();
    }

    private void createScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(28), dp(8), dp(28), dp(8));
        top.setBackgroundColor(PANEL);
        TextView brand = new TextView(this);
        brand.setText(SiteConfig.DISPLAY_NAME);
        brand.setTextColor(WHITE);
        brand.setTextSize(25);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        for (SiteConfig.Tab t : SiteConfig.TABS) {
            top.addView(tab(t.label, t.id, () -> { mode = t.id; render(); }), tabParams());
        }
        top.addView(tab("REFRESH", null, this::loadCatalog), tabParams());
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        status = text("Loading…", 16, MUTED, false);
        status.setPadding(dp(32), dp(12), dp(32), dp(8));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView hint = text("ARROWS BROWSE   •   OK SELECT   •   BACK RETURNS", 12, MUTED, false);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(dp(30), 0, dp(30), 0);
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        setContentView(root);
    }

    private LinearLayout.LayoutParams tabParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(128), dp(48));
        p.leftMargin = dp(8);
        return p;
    }

    private TextView tab(String label, String tabMode, Runnable click) {
        TextView v = text(label, 14, WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setOnClickListener(view -> click.run());
        if (tabMode != null) {
            v.setTag(tabMode);
            navTabs.add(v);
        }
        v.setBackground(buttonBackground(Color.rgb(41, 45, 58), ACCENT));
        return v;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private StateListDrawable buttonBackground(int normalColor, int selectedColor) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(dp(12));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(selectedColor);
        focused.setCornerRadius(dp(12));
        focused.setStroke(dp(3), FOCUS);
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(Color.rgb(55, 45, 85));
        selected.setCornerRadius(dp(12));
        selected.setStroke(dp(2), ACCENT);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_selected}, selected);
        states.addState(new int[]{}, normal);
        return states;
    }

    private void loadCatalog() {
        final int request = ++generation;
        mode = "home";
        activeCategory = null;
        images.evictAll();
        status.setText("Loading listings…");
        content.removeAllViews();
        network.execute(() -> {
            java.util.Map<String, List<CatalogItem>> fetched = new java.util.LinkedHashMap<>();
            List<CatalogCategory> genreList = Collections.emptyList();
            String error = null;
            for (SiteConfig.Tab t : SiteConfig.TABS) {
                if (t.path == null) continue;
                try { fetched.put(t.id, CatalogScraper.fetchItems(t.path)); }
                catch (Exception e) { if (error == null) error = message(e); }
            }
            try { genreList = CatalogScraper.fetchCategories(); }
            catch (Exception e) { if (error == null) error = message(e); }
            final java.util.Map<String, List<CatalogItem>> r = fetched;
            final List<CatalogCategory> g = genreList;
            final String problem = error;
            runOnUiThread(() -> {
                if (isFinishing() || request != generation) return;
                tabItems.clear(); tabItems.putAll(r);
                genres.clear(); genres.addAll(g);
                render();
                boolean everythingEmpty = genres.isEmpty();
                if (everythingEmpty) {
                    for (List<CatalogItem> list : tabItems.values()) {
                        if (!list.isEmpty()) { everythingEmpty = false; break; }
                    }
                }
                if (everythingEmpty && problem != null) {
                    status.setText("Could not load the catalog. Check the connection and tap REFRESH.");
                } else if (problem != null) {
                    status.setText("Some lists could not refresh. Tap REFRESH to try again.");
                } else {
                    int total = 0;
                    for (List<CatalogItem> list : tabItems.values()) total += list.size();
                    status.setText("Updated just now  ·  " + total + " titles  ·  " + genres.size() + " categories");
                }
            });
        });
    }

    private String message(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private SiteConfig.Tab findTab(String id) {
        for (SiteConfig.Tab t : SiteConfig.TABS) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    private void updateNavTabs() {
        for (TextView tab : navTabs) {
            String tabMode = (String) tab.getTag();
            boolean isSelected = mode.equals(tabMode) || ("category".equals(mode) && "genres".equals(tabMode));
            tab.setSelected(isSelected);
            tab.setContentDescription(tab.getText() + (isSelected ? ", current tab" : ", tab"));
        }
    }

    private void render() {
        updateNavTabs();
        content.removeAllViews();
        firstContentFocus = null;
        if ("genres".equals(mode)) {
            status.setText("Choose a category");
            addGenreGrid();
        } else if ("category".equals(mode)) {
            status.setText(activeCategory == null ? "Titles by category" : activeCategory);
            addSection(activeCategory == null ? "TITLES" : activeCategory.toUpperCase(), categoryItems);
        } else if (!"home".equals(mode)) {
            SiteConfig.Tab tab = findTab(mode);
            status.setText(tab == null ? "Titles" : tab.label);
            List<CatalogItem> items = tabItems.get(mode);
            addSection(tab == null ? mode : tab.label.toUpperCase(),
                    items == null ? Collections.<CatalogItem>emptyList() : items);
        } else {
            boolean anyItems = false;
            for (SiteConfig.Tab t : SiteConfig.TABS) {
                List<CatalogItem> items = tabItems.get(t.id);
                if (items != null && !items.isEmpty()) anyItems = true;
            }
            status.setText(anyItems ? "Choose a title to open its player" : "Loading…");
            for (SiteConfig.Tab t : SiteConfig.TABS) {
                List<CatalogItem> items = tabItems.get(t.id);
                if (items != null) addSection(t.label.toUpperCase(), items);
            }
            addGenreStrip();
        }
        if (content.getChildCount() == 0 && "genres".equals(mode)) {
            status.setText("Category list is unavailable. Tap REFRESH to try again.");
        } else if (content.getChildCount() == 0 && !"genres".equals(mode)) {
            addEmptyState();
        }
        View focus = firstContentFocus;
        if (focus != null) focus.post(() -> {
            if (focus.getParent() != null) focus.requestFocus();
        });
    }

    private void addSection(String heading, List<CatalogItem> all) {
        if (all.isEmpty()) return;
        TextView title = text(heading, 18, WHITE, true);
        title.setPadding(dp(30), dp(12), dp(30), dp(10));
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        HorizontalScrollView row = new HorizontalScrollView(this);
        row.setHorizontalScrollBarEnabled(false);
        row.setOverScrollMode(View.OVER_SCROLL_NEVER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(26), dp(8), dp(26), dp(14));
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        int count = Math.min(MAX_CARDS, all.size());
        for (int i = 0; i < count; i++) {
            View tile = card(all.get(i), i < 6);
            if (firstContentFocus == null) firstContentFocus = tile;
            cards.addView(tile, cardParams());
        }
        row.addView(cards, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(354)));
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(198), dp(330));
        p.rightMargin = dp(14);
        return p;
    }

    private View card(CatalogItem item, boolean preload) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClipToOutline(true);
        card.setBackground(buttonBackground(CARD, Color.rgb(45, 39, 75)));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.rgb(38, 44, 58));
        poster.setTag(item.posterUrl);
        card.addView(poster, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                    .setDuration(110).start();
            if (focused) loadPoster(item.posterUrl, poster);
        });
        card.setOnClickListener(v -> open(item));
        TextView name = text(item.title, 15, WHITE, true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(dp(12), dp(10), dp(12), 0);
        card.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView meta = text(item.details, 12, MUTED, false);
        meta.setMaxLines(1);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.setPadding(dp(12), dp(4), dp(12), dp(6));
        card.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        if (preload) loadPoster(item.posterUrl, poster);
        card.setContentDescription(item.title + ", " + item.details + ", play");
        return card;
    }

    private void loadPoster(String url, ImageView target) {
        if (url == null) return;
        boolean allowed = false;
        for (String prefix : SiteConfig.ALLOWED_IMAGE_PREFIXES) {
            if (url.startsWith(prefix)) { allowed = true; break; }
        }
        if (!allowed) return;
        Bitmap cached = images.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        if (!loadingImages.add(url)) return;
        imageWorkers.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setUseCaches(false);
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)");
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
                connection.setRequestProperty("Pragma", "no-cache");
                try (InputStream in = connection.getInputStream()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
                    if (bitmap != null) {
                        images.put(url, bitmap);
                        runOnUiThread(() -> {
                            if (url.equals(target.getTag())) target.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
                loadingImages.remove(url);
            }
        });
    }

    private void addGenreStrip() {
        if (genres.isEmpty()) return;
        TextView heading = text("BROWSE BY CATEGORY", 18, WHITE, true);
        heading.setPadding(dp(30), dp(14), dp(30), dp(10));
        content.addView(heading);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(26), dp(4), dp(26), dp(12));
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Math.min(8, genres.size()); i++) {
            row.addView(genreButton(genres.get(i)), genreParams());
        }
        content.addView(row);
    }

    private void addGenreGrid() {
        if (genres.isEmpty()) return;
        LinearLayout line = null;
        int columns = 4;
        for (int i = 0; i < genres.size(); i++) {
            if (i % columns == 0) {
                line = new LinearLayout(this);
                line.setPadding(dp(26), dp(8), dp(26), dp(8));
                content.addView(line);
            }
            View button = genreButton(genres.get(i));
            if (firstContentFocus == null) firstContentFocus = button;
            line.addView(button, genreParams());
        }
    }

    private LinearLayout.LayoutParams genreParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(205), dp(60));
        p.rightMargin = dp(12);
        return p;
    }

    private TextView genreButton(CatalogCategory genre) {
        TextView button = text(genre.title, 15, WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setOnClickListener(v -> loadGenre(genre));
        button.setBackground(buttonBackground(CARD, ACCENT));
        return button;
    }

    private void loadGenre(CatalogCategory genre) {
        final int request = ++generation;
        mode = "category";
        activeCategory = genre.title;
        status.setText("Loading " + genre.title + "…");
        content.removeAllViews();
        network.execute(() -> {
            List<CatalogItem> items;
            String error = null;
            try { items = CatalogScraper.fetchItems(genre.path); }
            catch (Exception e) { items = Collections.emptyList(); error = message(e); }
            final List<CatalogItem> found = items;
            final String problem = error;
            runOnUiThread(() -> {
                if (isFinishing() || request != generation) return;
                categoryItems.clear();
                categoryItems.addAll(found);
                render();
                if (found.isEmpty() && problem != null) {
                    status.setText("Could not load this category. Press GENRES to choose another.");
                }
            });
        });
    }

    private void open(CatalogItem item) {
        android.content.Intent intent = new android.content.Intent(this, WatchActivity.class);
        intent.putExtra("url", item.watchUrl);
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ("category".equals(mode)) {
                mode = "genres";
                render();
                return true;
            }
            if (!"home".equals(mode)) {
                mode = "home";
                render();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        generation++;
        network.shutdownNow();
        imageWorkers.shutdownNow();
        super.onDestroy();
    }

    private void addEmptyState() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), dp(48), dp(32), dp(48));

        TextView msg = text("No titles available right now", 18, MUTED, false);
        msg.setGravity(Gravity.CENTER);
        empty.addView(msg);

        TextView retry = text("RETRY", 14, WHITE, true);
        retry.setGravity(Gravity.CENTER);
        retry.setFocusable(true);
        retry.setContentDescription("Retry loading catalog");
        retry.setOnClickListener(v -> loadCatalog());
        retry.setBackground(buttonBackground(Color.rgb(41, 45, 58), ACCENT));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dp(140), dp(48));
        btnParams.topMargin = dp(18);
        empty.addView(retry, btnParams);

        if (firstContentFocus == null) {
            firstContentFocus = retry;
        }
        content.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
