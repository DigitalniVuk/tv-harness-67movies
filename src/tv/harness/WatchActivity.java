package tv.harness;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compact player screen with native, focusable remote controls and overlay episode picker. */
public final class WatchActivity extends Activity {
    private static final String HOME_URL = SiteConfig.BASE_URL + SiteConfig.HOME_PATH;
    private static final int BG = Color.rgb(10, 12, 18);
    private static final int ACCENT = Color.rgb(126, 91, 242);
    private static final int FOCUS = Color.rgb(83, 184, 255);
    private static final int GLASS_BG = Color.argb(205, 14, 18, 27);
    private static final int GLASS_BTN = Color.argb(215, 30, 35, 48);
    private static final int AUTO_HIDE_DELAY_MS = 4000;

    private static class EpisodeInfo {
        final String num;
        final String name;

        EpisodeInfo(String num, String name) {
            this.num = num;
            this.name = name;
        }
    }

    private FrameLayout rootOverlay;
    private LinearLayout toolbar;
    private LinearLayout controls;
    private LinearLayout episodeSheet;
    private LinearLayout episodeListContainer;
    private HorizontalScrollView episodeScrollView;
    private TextView episodeSheetTitle;
    private TextView title;
    private TextView autoplayButton;
    private ProgressBar progress;
    private WebView web;
    private FrameLayout playerArea;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean autoplayOn;

    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsRunnable = this::hideOsd;
    private boolean osdVisible = true;
    private boolean episodeSheetVisible = false;

    // Direct-embed navigation + stuck-media watchdog (see SiteConfig).
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable embedPollRunnable = this::pollForEmbed;
    private final Runnable stallRunnable = this::stallTick;
    private boolean directNavigated;
    private int embedPollTries;
    private long stallSince = -1;
    private int stallAttempts;
    private boolean stallExhaustedNotified;
    private boolean autoUnmuted;
    private final List<EpisodeInfo> loadedEpisodes = new ArrayList<>();
    private String currentEpisodeNum = "";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        configureWebView();
        if (state == null) web.loadUrl(startUrl());
        else web.restoreState(state);
        scheduleHideOsd();
    }

    private void buildUi() {
        rootOverlay = new FrameLayout(this);
        rootOverlay.setBackgroundColor(BG);

        playerArea = new FrameLayout(this);
        playerArea.setBackgroundColor(BG);
        rootOverlay.addView(playerArea, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        web = new WebView(this);
        web.setId(View.generateViewId());
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setBackgroundColor(BG);
        playerArea.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setBackground(topScrimDrawable());

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(24), dp(10), dp(24), dp(8));

        title = new TextView(this);
        title.setText(SiteConfig.DISPLAY_NAME + " PLAYER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        topBar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!SiteConfig.EPISODE_LIST_SELECTOR.isEmpty()) {
            topBar.addView(action("EPISODES", this::toggleEpisodeSheet), headerButtonParams());
        }
        topBar.addView(action("RELOAD", () -> web.reload()), headerButtonParams());
        topBar.addView(action("BROWSE", this::finish), headerButtonParams());
        toolbar.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        toolbar.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        rootOverlay.addView(toolbar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        episodeSheet = new LinearLayout(this);
        episodeSheet.setOrientation(LinearLayout.VERTICAL);
        episodeSheet.setBackground(sheetBackgroundDrawable());
        episodeSheet.setPadding(dp(20), dp(16), dp(20), dp(16));
        episodeSheet.setVisibility(View.GONE);

        LinearLayout sheetHeader = new LinearLayout(this);
        sheetHeader.setGravity(Gravity.CENTER_VERTICAL);
        episodeSheetTitle = new TextView(this);
        episodeSheetTitle.setText("SELECT EPISODE");
        episodeSheetTitle.setTextColor(Color.WHITE);
        episodeSheetTitle.setTextSize(16);
        episodeSheetTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sheetHeader.addView(episodeSheetTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sheetHeader.addView(action("CLOSE ✕", this::toggleEpisodeSheet), headerButtonParams());
        episodeSheet.addView(sheetHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        episodeScrollView = new HorizontalScrollView(this);
        episodeScrollView.setHorizontalScrollBarEnabled(false);
        episodeScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        episodeListContainer = new LinearLayout(this);
        episodeListContainer.setOrientation(LinearLayout.HORIZONTAL);
        episodeListContainer.setPadding(0, dp(12), 0, dp(4));
        episodeScrollView.addView(episodeListContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        episodeSheet.addView(episodeScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sheetParams.bottomMargin = dp(88);
        sheetParams.leftMargin = dp(24);
        sheetParams.rightMargin = dp(24);
        rootOverlay.addView(episodeSheet, sheetParams);

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(16), dp(10), dp(16), dp(12));
        controls.setBackground(bottomScrimDrawable());

        controls.addView(square("Ⅱ\nPAUSE / PLAY", this::togglePause), controlParams());
        controls.addView(square("◀\nPREV", () -> pageClick(SiteConfig.PREV_EPISODE_SELECTOR)), controlParams());
        controls.addView(square("▶\nNEXT", () -> pageClick(SiteConfig.NEXT_EPISODE_SELECTOR)), controlParams());
        if (!SiteConfig.EPISODE_LIST_SELECTOR.isEmpty()) {
            controls.addView(square("≡\nEPISODES", this::toggleEpisodeSheet), controlParams());
        }
        autoplayButton = square("AUTO PLAY\n…", this::toggleAutoplay);
        controls.addView(autoplayButton, controlParams());
        if (!SiteConfig.STALL_FALLBACK_SELECTOR.isEmpty()) {
            controls.addView(square("⟳\nSERVER", this::tryServer), controlParams());
        }
        if (!SiteConfig.SOUND_CONTROL_SELECTOR.isEmpty()) {
            controls.addView(square("🔊\nSOUND", this::toggleSound), controlParams());
        }

        rootOverlay.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86), Gravity.BOTTOM));

        setContentView(rootOverlay);
        web.requestFocus();
    }

    private GradientDrawable topScrimDrawable() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(220, 10, 12, 18), Color.argb(160, 10, 12, 18), Color.TRANSPARENT});
    }

    private GradientDrawable bottomScrimDrawable() {
        return new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.argb(235, 10, 12, 18), Color.argb(175, 10, 12, 18), Color.TRANSPARENT});
    }

    private GradientDrawable sheetBackgroundDrawable() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GLASS_BG);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        return bg;
    }

    private LinearLayout.LayoutParams headerButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(106), dp(38));
        p.leftMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams controlParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(64), 1);
        p.leftMargin = dp(6);
        p.rightMargin = dp(6);
        return p;
    }

    private TextView square(String label, Runnable run) {
        TextView button = action(label, run);
        button.setTextSize(13);
        button.setMaxLines(2);
        button.setId(View.generateViewId());
        return button;
    }

    private TextView action(String label, Runnable run) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setOnClickListener(v -> {
            scheduleHideOsd();
            run.run();
            if (!isFinishing()) v.post(v::requestFocus);
        });
        button.setBackground(buttonBackground());
        return button;
    }

    private StateListDrawable buttonBackground() {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(GLASS_BTN);
        normal.setCornerRadius(dp(10));
        normal.setStroke(dp(1), Color.argb(60, 255, 255, 255));

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(ACCENT);
        focused.setCornerRadius(dp(10));
        focused.setStroke(dp(3), FOCUS);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private void scheduleHideOsd() {
        hideHandler.removeCallbacks(hideControlsRunnable);
        if (episodeSheetVisible) {
            // Keep controls visible while selecting episodes
            return;
        }
        hideHandler.postDelayed(hideControlsRunnable, AUTO_HIDE_DELAY_MS);
    }

    private void showOsd() {
        osdVisible = true;
        toolbar.setVisibility(View.VISIBLE);
        controls.setVisibility(View.VISIBLE);
        scheduleHideOsd();
    }

    private void hideOsd() {
        if (episodeSheetVisible) return;
        osdVisible = false;
        toolbar.setVisibility(View.GONE);
        controls.setVisibility(View.GONE);
        if (web != null && !web.hasFocus()) {
            web.requestFocus();
        }
    }

    private void toggleEpisodeSheet() {
        if (SiteConfig.EPISODE_LIST_SELECTOR.isEmpty()) {
            Toast.makeText(this, "Episodes not available for this site", Toast.LENGTH_SHORT).show();
            return;
        }
        episodeSheetVisible = !episodeSheetVisible;
        if (episodeSheetVisible) {
            showOsd();
            hideHandler.removeCallbacks(hideControlsRunnable);
            episodeSheet.setVisibility(View.VISIBLE);
            fetchEpisodesFromPage();
            if (episodeListContainer.getChildCount() > 0) {
                focusCurrentEpisodeOrFirst();
            }
        } else {
            episodeSheet.setVisibility(View.GONE);
            scheduleHideOsd();
            if (controls.getChildCount() > 0) {
                controls.getChildAt(0).requestFocus();
            }
        }
    }

    private void focusCurrentEpisodeOrFirst() {
        View target = null;
        for (int i = 0; i < episodeListContainer.getChildCount(); i++) {
            View child = episodeListContainer.getChildAt(i);
            if (currentEpisodeNum != null && currentEpisodeNum.equals(child.getTag())) {
                target = child;
                break;
            }
        }
        if (target == null && episodeListContainer.getChildCount() > 0) {
            target = episodeListContainer.getChildAt(0);
        }
        if (target != null) {
            final View toFocus = target;
            toFocus.post(() -> {
                toFocus.requestFocus();
                int scrollTo = toFocus.getLeft() - dp(40);
                episodeScrollView.smoothScrollTo(Math.max(0, scrollTo), 0);
            });
        }
    }

    private void fetchEpisodesFromPage() {
        if (web == null) return;
        if (SiteConfig.EPISODE_LIST_SELECTOR.isEmpty()) return;
        String js = "(function(){"
                + "var eps=[];"
                + "var active=document.querySelector(" + jsQuote(SiteConfig.EPISODE_ACTIVE_SELECTOR) + ");"
                + "var cur=active?(active.getAttribute(" + jsQuote(SiteConfig.EPISODE_NUM_ATTR) + ")||'') :'';"
                + "var nodes=document.querySelectorAll(" + jsQuote(SiteConfig.EPISODE_LIST_SELECTOR) + ");"
                + "for(var i=0;i<nodes.length;i++){"
                + "  var n=nodes[i];"
                + "  var num=n.getAttribute(" + jsQuote(SiteConfig.EPISODE_NUM_ATTR) + ")||n.textContent.trim();"
                + "  var name=n.getAttribute('title')||n.textContent.trim();"
                + "  if(num) eps.push({num:num,name:name});"
                + "}"
                + "return JSON.stringify({current:cur,episodes:eps});"
                + "})()";
        web.evaluateJavascript(js, result -> {
            if (result == null || "null".equals(result)) return;
            try {
                String unquoted = new JSONObject("{data:" + result + "}").getString("data");
                JSONObject json = new JSONObject(unquoted);
                currentEpisodeNum = json.optString("current", "");
                JSONArray arr = json.getJSONArray("episodes");
                loadedEpisodes.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    loadedEpisodes.add(new EpisodeInfo(o.getString("num"), o.getString("name")));
                }
                populateEpisodeUi();
            } catch (Exception ignored) {
            }
        });
    }

    private void populateEpisodeUi() {
        episodeListContainer.removeAllViews();
        if (loadedEpisodes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Episodes are still loading from source...");
            empty.setTextColor(Color.rgb(180, 185, 200));
            empty.setTextSize(14);
            empty.setPadding(dp(10), dp(10), dp(10), dp(10));
            episodeListContainer.addView(empty);
            return;
        }

        episodeSheetTitle.setText("SELECT EPISODE (" + loadedEpisodes.size() + " TOTAL)");

        for (EpisodeInfo ep : loadedEpisodes) {
            final String num = ep.num;
            boolean isCurrent = num.equals(currentEpisodeNum);

            TextView btn = new TextView(this);
            btn.setText("EP " + num);
            btn.setTag(num);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(13);
            btn.setTypeface(Typeface.DEFAULT, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
            btn.setGravity(Gravity.CENTER);
            btn.setFocusable(true);
            btn.setFocusableInTouchMode(true);

            GradientDrawable norm = new GradientDrawable();
            norm.setColor(isCurrent ? ACCENT : GLASS_BTN);
            norm.setCornerRadius(dp(8));
            norm.setStroke(dp(1), isCurrent ? FOCUS : Color.argb(60, 255, 255, 255));

            GradientDrawable foc = new GradientDrawable();
            foc.setColor(ACCENT);
            foc.setCornerRadius(dp(8));
            foc.setStroke(dp(3), FOCUS);

            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, foc);
            states.addState(new int[]{android.R.attr.state_pressed}, foc);
            states.addState(new int[]{}, norm);
            btn.setBackground(states);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(44));
            lp.rightMargin = dp(8);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> selectEpisode(num));
            episodeListContainer.addView(btn);
        }

        focusCurrentEpisodeOrFirst();
    }

    private void selectEpisode(String num) {
        currentEpisodeNum = num;
        toggleEpisodeSheet();
        Toast.makeText(this, "Loading Episode " + num + "…", Toast.LENGTH_SHORT).show();
        String js = "(function(){"
                + "var sel=" + jsQuote(SiteConfig.EPISODE_LIST_SELECTOR) + ";"
                + "var el=null;"
                + "var nodes=document.querySelectorAll(sel);"
                + "for(var i=0;i<nodes.length;i++){"
                + "  if((nodes[i].getAttribute(" + jsQuote(SiteConfig.EPISODE_NUM_ATTR) + ")||nodes[i].textContent.trim())==="
                + jsQuote(num) + "){el=nodes[i];break;}"
                + "}"
                + "if(el){el.click();return true;}"
                + "return false;"
                + "})()";
        web.evaluateJavascript(js, res -> {
            if ("false".equals(res)) {
                Toast.makeText(this, "Episode " + num + " not clickable yet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setLoadsImagesAutomatically(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request.isForMainFrame() && AdBlocker.blocks(request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                               WebResourceRequest request) {
                return blockedResponse(request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return blockedResponse(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host != null) title.setText(host + "  ·  PLAYER");
                view.evaluateJavascript(playerPageScript(), null);
                updateAutoplayState();
                fetchEpisodesFromPage();
                if (!url.contains(SiteConfig.DIRECT_EMBED_HOST)) {
                    // Back on the wrapper page (e.g. after BACK): allow a
                    // fresh direct-nav the next time Play is pressed.
                    if (url.startsWith(SiteConfig.BASE_URL)) directNavigated = false;
                    armEmbedPoller();
                } else {
                    // Just landed on the embed page: watch its loading
                    // state and auto-cycle servers if it hangs.
                    stallSince = SystemClock.elapsedRealtime();
                    stallAttempts = 0;
                    stallExhaustedNotified = false;
                    autoUnmuted = false;
                    pollHandler.removeCallbacks(stallRunnable);
                    pollHandler.postDelayed(stallRunnable, 2000);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(WatchActivity.this, "Secure connection failed", Toast.LENGTH_SHORT).show();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture,
                                          android.os.Message resultMsg) {
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                web.setVisibility(View.GONE);
                playerArea.addView(view, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                leaveFullscreen();
            }

            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message,
                                     android.webkit.JsResult result) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message,
                                       android.webkit.JsResult result) {
                result.cancel();
                return true;
            }
        });
    }

    private WebResourceResponse blockedResponse(String url) {
        if (!AdBlocker.blocks(url)) return null;
        return new WebResourceResponse("text/plain", "utf-8", 204, "No Content",
                Collections.<String, String>emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private void pageClick(String selector) {
        if (selector == null || selector.isEmpty()) {
            Toast.makeText(this, "That control is not available for this site", Toast.LENGTH_SHORT).show();
            return;
        }
        String js = "(function(){var e=document.querySelector(" + jsQuote(selector)
                + ");if(e){e.click();return true;}return false;})()";
        web.evaluateJavascript(js, value -> {
            if ("false".equals(value)) Toast.makeText(this, "Episode control is not available yet",
                    Toast.LENGTH_SHORT).show();
            else fetchEpisodesFromPage();
        });
    }

    private static String jsQuote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private void toggleAutoplay() {
        if (SiteConfig.AUTOPLAY_SELECTOR == null || SiteConfig.AUTOPLAY_SELECTOR.isEmpty()) {
            Toast.makeText(this, "Autoplay control is not available for this site", Toast.LENGTH_SHORT).show();
            return;
        }
        web.evaluateJavascript("(function(){var e=document.querySelector("
                + jsQuote(SiteConfig.AUTOPLAY_SELECTOR) + ");"
                + "if(!e)return false;e.click();return e.classList.contains('active')||"
                + "e.classList.contains('on')||e.getAttribute('data-value')==='1';})()",
                value -> updateAutoplayState());
    }

    private void updateAutoplayState() {
        if (web == null || autoplayButton == null) return;
        web.evaluateJavascript("(function(){var e=document.querySelector("
                + jsQuote(SiteConfig.AUTOPLAY_SELECTOR) + ");"
                + "var v=localStorage.getItem('auto_play');if(v!==null)return v==='1'||v==='true';"
                + "return !!(e&&(e.classList.contains('active')||e.classList.contains('on')||"
                + "e.getAttribute('data-value')==='1'||e.getAttribute('aria-checked')==='true'));})()",
                value -> {
                    autoplayOn = "true".equals(value);
                    if (autoplayButton != null) autoplayButton.setText(
                            "AUTO PLAY\n" + (autoplayOn ? "ON ✓" : "OFF"));
                });
    }

    private void togglePause() {
        // On the direct embed page, use its own play/pause control.
        if (isDirectEmbed() && !SiteConfig.EMBED_PLAY_SELECTOR.isEmpty()) {
            web.evaluateJavascript("(function(){var e=document.querySelector("
                    + jsQuote(SiteConfig.EMBED_PLAY_SELECTOR) + ");if(e){e.click();return true;}return false;})()", null);
            return;
        }
        // Prefer the site's own pause control; otherwise bridge Space to
        // the player (many sites listen for it on the watch page).
        String selector = SiteConfig.PAUSE_TOGGLE_SELECTOR;
        if (selector != null && !selector.isEmpty()) {
            web.evaluateJavascript("(function(){var e=document.querySelector("
                    + jsQuote(selector) + ");if(e){e.click();return true;}return false;})()", null);
            return;
        }
        web.evaluateJavascript("(function(){var e=document.createEvent('Events');"
                + "e.initEvent('keydown',true,true);Object.defineProperty(e,'keyCode',{get:function(){return 32;}});"
                + "Object.defineProperty(e,'which',{get:function(){return 32;}});window.dispatchEvent(e);return true;})()",
                null);
    }

    /** Manual server switch (same action the watchdog takes automatically). */
    private void tryServer() {
        if (SiteConfig.STALL_FALLBACK_SELECTOR.isEmpty()) {
            Toast.makeText(this, "Server switching not available for this site", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Switching server…", Toast.LENGTH_SHORT).show();
        pageClick(SiteConfig.STALL_FALLBACK_SELECTOR);
        stallSince = SystemClock.elapsedRealtime();
    }

    /** Manual mute/unmute toggle of the embed player. */
    private void toggleSound() {
        if (SiteConfig.SOUND_CONTROL_SELECTOR.isEmpty()) {
            Toast.makeText(this, "Sound control not available for this site", Toast.LENGTH_SHORT).show();
            return;
        }
        web.evaluateJavascript("(function(){var e=document.querySelector(" + jsQuote(SiteConfig.SOUND_CONTROL_SELECTOR)
                + ");if(e){e.click();return true;}return false;})()", null);
    }

    private boolean isDirectEmbed() {
        if (web == null || web.getUrl() == null || SiteConfig.DIRECT_EMBED_HOST.isEmpty()) return false;
        return web.getUrl().contains(SiteConfig.DIRECT_EMBED_HOST);
    }

    /**
     * While on the wrapper page, poll for the embed iframe and open its
     * URL directly so the player chrome lives in this document (a
     * cross-origin iframe cannot be driven by page JS otherwise).
     */
    private void armEmbedPoller() {
        if (SiteConfig.DIRECT_EMBED_HOST.isEmpty() || directNavigated) return;
        embedPollTries = 0;
        pollHandler.removeCallbacks(embedPollRunnable);
        pollHandler.postDelayed(embedPollRunnable, 1000);
    }

    private void pollForEmbed() {
        if (directNavigated || web == null) return;
        if (++embedPollTries > 120) return; // ~2 min give-up
        String host = SiteConfig.DIRECT_EMBED_HOST;
        String js = "(function(){var fs=document.querySelectorAll('iframe');"
                + "for(var i=0;i<fs.length;i++){var s=fs[i].src||'';"
                + "if(s.indexOf(" + jsQuote(host) + ")>=0)return s;}return '';})()";
        web.evaluateJavascript(js, value -> {
            if (directNavigated || web == null) return;
            if (value == null || "''".equals(value) || "null".equals(value)) {
                pollHandler.postDelayed(embedPollRunnable, 1000);
                return;
            }
            // Unquote the JSON string the WebView returns.
            String src = "";
            try {
                src = new JSONObject("{data:" + value + "}").getString("data");
            } catch (Exception ignored) {
            }
            if (src.isEmpty() || src.equals(web.getUrl())) {
                pollHandler.postDelayed(embedPollRunnable, 1000);
                return;
            }
            directNavigated = true;
            Toast.makeText(this, "Opening player…", Toast.LENGTH_SHORT).show();
            web.loadUrl(src);
        });
    }

    /**
     * Stuck-media watchdog: while the embed page has made no video
     * progress and its "try another server" control is offered, click
     * it (cycle server) a bounded number of times. When playback does
     * start muted, unmute once automatically.
     */
    private void stallTick() {
        if (web == null) return;
        if (!isDirectEmbed()) return; // user navigated away (e.g. BACK)
        String js = "(function(){var v=document.querySelector('video');"
                + "var started=!!(v&&(v.currentTime>0.2));"
                + "var muted=!!(v&&v.muted);"
                + "var fb=document.querySelector(" + jsQuote(SiteConfig.STALL_FALLBACK_SELECTOR) + ");"
                + "var fbOn=!!(fb&&fb.classList.contains('is-on')&&fb.getAttribute('aria-hidden')!=='true');"
                + "return JSON.stringify({started:started,muted:muted,fbOn:fbOn});})()";
        web.evaluateJavascript(js, value -> {
            if (web == null || !isDirectEmbed()) return;
            boolean started = false, muted = false, fbOn = false;
            try {
                String unquoted = new JSONObject("{data:" + value + "}").getString("data");
                JSONObject state = new JSONObject(unquoted);
                started = state.optBoolean("started", false);
                muted = state.optBoolean("muted", false);
                fbOn = state.optBoolean("fbOn", false);
            } catch (Exception ignored) {
            }
            long now = SystemClock.elapsedRealtime();
            if (started) {
                stallSince = -1;
                if (muted && !SiteConfig.SOUND_CONTROL_SELECTOR.isEmpty() && !autoUnmuted) {
                    autoUnmuted = true;
                    web.evaluateJavascript("(function(){var e=document.querySelector(" + jsQuote(SiteConfig.SOUND_CONTROL_SELECTOR)
                            + ");if(e){e.click();}return true;})()", null);
                    Toast.makeText(this, "Sound on", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (stallSince < 0) stallSince = now;
                long idleMs = now - stallSince;
                long neededMs = stallAttempts == 0 ? SiteConfig.STALL_FIRST_TIMEOUT_MS : SiteConfig.STALL_RETRY_MS;
                if (fbOn && idleMs >= neededMs && stallAttempts < SiteConfig.STALL_MAX_CLICKS) {
                    stallAttempts++;
                    stallSince = now;
                    web.evaluateJavascript("(function(){var e=document.querySelector(" + jsQuote(SiteConfig.STALL_FALLBACK_SELECTOR)
                            + ");if(e){e.click();}return true;})()", null);
                    Toast.makeText(this, "Trying another server (" + stallAttempts + "/" + SiteConfig.STALL_MAX_CLICKS + ")", Toast.LENGTH_SHORT).show();
                } else if (stallAttempts >= SiteConfig.STALL_MAX_CLICKS && !stallExhaustedNotified) {
                    stallExhaustedNotified = true;
                    Toast.makeText(this, "No working server found — check the site or your connection", Toast.LENGTH_LONG).show();
                }
            }
            pollHandler.postDelayed(stallRunnable, 2000);
        });
    }

    /** Builds the page-customisation script from SiteConfig player selectors. */
    private static String playerPageScript() {
        String hide = SiteConfig.PLAYER_HIDE_CSS == null ? "" : SiteConfig.PLAYER_HIDE_CSS;
        String keep = SiteConfig.PLAYER_KEEP_SELECTORS == null ? "" : SiteConfig.PLAYER_KEEP_SELECTORS;
        String element = SiteConfig.PLAYER_ELEMENT_SELECTOR == null ? "" : SiteConfig.PLAYER_ELEMENT_SELECTOR;
        String frame = SiteConfig.PLAYER_FRAME_SELECTOR == null ? "" : SiteConfig.PLAYER_FRAME_SELECTOR;
        String focus = SiteConfig.PLAYER_FOCUS_SELECTOR == null ? "" : SiteConfig.PLAYER_FOCUS_SELECTOR;
        StringBuilder css = new StringBuilder()
                .append("html,body{margin:0!important;padding:0!important;overflow:hidden!important;")
                .append("background:#0a0c12!important;width:100%!important;height:100%!important;}");
        if (!hide.isEmpty()) {
            css.append(hide).append("{display:none!important;}");
        }
        if (!keep.isEmpty()) {
            css.append(keep).append("{display:block!important;position:static!important;")
                    .append("width:100%!important;max-width:none!important;")
                    .append("height:100%!important;min-height:0!important;margin:0!important;padding:0!important;")
                    .append("border:0!important;background:#0a0c12!important;overflow:hidden!important;}");
        }
        if (!element.isEmpty()) {
            css.append(element).append("{height:100%!important;width:100%!important;display:flex!important;")
                    .append("flex-direction:column!important;justify-content:center!important;align-items:center!important;}");
        }
        if (!frame.isEmpty()) {
            css.append(frame).append("{position:absolute!important;inset:0!important;")
                    .append("width:100%!important;height:100%!important;border:0!important;}");
        }
        return "(function(){"
                + "var css=" + jsQuote(css.toString()) + ";"
                + "var s=document.getElementById('tv-player-style');if(!s){s=document.createElement('style');"
                + "s.id='tv-player-style';(document.head||document.documentElement).appendChild(s);}s.textContent=css;"
                + "try{window.open=function(){return null};window.alert=function(){};}catch(e){}"
                // Keep watching for the focus target: SPA details pages
                // render long after onPageFinished (client-side data
                // fetch), so a short timeout misses the Play button.
                // Safety cap ~4 min.
                + "if(!window.__tvFocusWatch){window.__tvFocusWatch=1;var n=0;var t=setInterval(function(){"
                + "var p=" + (focus.isEmpty() ? "null" : "document.querySelector(" + jsQuote(focus) + ")") + ";"
                + "if(p&&getComputedStyle(p).display!=='none'){"
                + "if(p.scrollIntoView)p.scrollIntoView({block:'center'});"
                + "p.setAttribute('tabindex','0');p.focus();clearInterval(t);}else{"
                + "p=document.querySelector(" + jsQuote(frame.isEmpty() ? "iframe, video" : frame) + ");"
                + "if(p){p.setAttribute('tabindex','0');"
                + "p.focus();clearInterval(t);}}if(++n>480)clearInterval(t);},500);}"
                + "})();";
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();

        // Handle BACK key
        if (key == KeyEvent.KEYCODE_BACK) {
            if (fullscreenView != null) {
                leaveFullscreen();
                return true;
            }
            if (episodeSheetVisible) {
                toggleEpisodeSheet();
                return true;
            }
            if (osdVisible) {
                hideOsd();
                return true;
            }
            if (web != null && web.canGoBack()) {
                web.goBack();
                return true;
            }
        }

        // Media keys
        if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            togglePause();
            scheduleHideOsd();
            return true;
        }

        // D-Pad and navigation keys wake up / keep OSD visible
        boolean isDpadKey = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;

        if (isDpadKey) {
            if (!osdVisible) {
                showOsd();
                controls.getChildAt(0).requestFocus();
                return true;
            }
            scheduleHideOsd();
        }

        // If episode sheet is open, manage navigation in episode list
        if (episodeSheetVisible) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                controls.getChildAt(0).requestFocus();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                toolbar.getChildAt(0).requestFocus();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // Navigation between controls and top bar
        if (controls.hasFocus()) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (toolbar.getVisibility() == View.VISIBLE) {
                    View topBar = toolbar.getChildAt(0);
                    if (topBar instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) topBar;
                        for (int i = 0; i < group.getChildCount(); i++) {
                            View c = group.getChildAt(i);
                            if (c.isFocusable()) {
                                c.requestFocus();
                                return true;
                            }
                        }
                    }
                }
                web.requestFocus();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                View focused = getCurrentFocus();
                int index = controls.indexOfChild(focused);
                if (index >= 0) {
                    int delta = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                    int next = Math.max(0, Math.min(controls.getChildCount() - 1, index + delta));
                    controls.getChildAt(next).requestFocus();
                    return true;
                }
            }
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                View focused = getCurrentFocus();
                if (focused != null && controls.indexOfChild(focused) >= 0) {
                    focused.performClick();
                    return true;
                }
            }
        } else if (toolbar.hasFocus()) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                controls.getChildAt(0).requestFocus();
                return true;
            }
        }

        if (key == KeyEvent.KEYCODE_DPAD_DOWN && !controls.hasFocus()) {
            showOsd();
            controls.getChildAt(0).requestFocus();
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private String startUrl() {
        String requested = getIntent().getStringExtra("url");
        if (requested != null && requested.startsWith(SiteConfig.BASE_URL + "/")) {
            return requested;
        }
        return HOME_URL;
    }

    private void leaveFullscreen() {
        if (fullscreenView == null) return;
        playerArea.removeView(fullscreenView);
        fullscreenView = null;
        web.setVisibility(View.VISIBLE);
        web.requestFocus();
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onDestroy() {
        hideHandler.removeCallbacks(hideControlsRunnable);
        pollHandler.removeCallbacks(embedPollRunnable);
        pollHandler.removeCallbacks(stallRunnable);
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.clearHistory();
            web.removeAllViews();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
