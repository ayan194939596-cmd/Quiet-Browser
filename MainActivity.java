package com.quiet.browser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {
    static final String HOME = "https://duckduckgo.com";
    static final String DESK = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    static final String[] BL = {"doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagmanager.com", "googletagservices.com", "adservice.google.com", "connect.facebook.net", "taboola.com",
        "outbrain.com", "adnxs.com", "criteo.com", "criteo.net", "scorecardresearch.com", "hotjar.com", "amazon-adsystem.com",
        "ads-twitter.com", "rubiconproject.com", "pubmatic.com", "openx.net", "moatads.com", "quantserve.com", "adsrvr.org",
        "analytics.yahoo.com", "fullstory.com", "mixpanel.com", "mathtag.com", "adcolony.com", "popads.net", "propellerads.com"};

    WebView w;
    EditText url;
    ProgressBar pb;
    FrameLayout frame;
    LinearLayout root;
    SharedPreferences sp;
    boolean quiet, desktop;
    volatile int blocked;
    String mobUA;
    View cv;
    WebChromeClient.CustomViewCallback cb;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    TextView btn(String t, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(t);
        b.setTextColor(0xFFE6EDF3);
        b.setTextSize(24);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(44)));
        return b;
    }

    boolean bad(String h) {
        if (h == null) return false;
        for (String d : BL) if (h.equals(d) || h.endsWith("." + d)) return true;
        return false;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("q", 0);
        quiet = sp.getBoolean("quiet", true);
        desktop = sp.getBoolean("desk", false);
        getWindow().setStatusBarColor(0xFF1C2128);
        getWindow().setNavigationBarColor(0xFF1C2128);

        w = new WebView(this);
        url = new EditText(this);
        pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgressTintList(ColorStateList.valueOf(0xFF9FE8D3));
        pb.setVisibility(View.INVISIBLE);
        pb.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(3)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF1C2128);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.addView(btn("\u2039", v -> { if (w.canGoBack()) w.goBack(); }));
        bar.addView(btn("\u203A", v -> { if (w.canGoForward()) w.goForward(); }));
        url.setSingleLine(true);
        url.setTextColor(0xFFE6EDF3);
        url.setHintTextColor(0xFF8B949E);
        url.setHint("Search or enter address");
        url.setTextSize(15);
        url.setSelectAllOnFocus(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_GO);
        url.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF2D333B);
        g.setCornerRadius(dp(20));
        url.setBackground(g);
        url.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1));
        url.setOnEditorActionListener((v, a, e) -> { go(url.getText().toString()); return true; });
        bar.addView(url);
        bar.addView(btn("\u21BB", v -> { if (pb.getVisibility() == View.VISIBLE) w.stopLoading(); else w.reload(); }));
        bar.addView(btn("\u22EE", this::menu));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        root.setFocusableInTouchMode(true);
        root.addView(bar);
        root.addView(pb);
        w.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(w);
        frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setGeolocationEnabled(false);
        s.setAllowFileAccess(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        mobUA = s.getUserAgentString().replace("; wv", "");
        applyUA();
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, false);

        w.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (quiet && bad(r.getUrl().getHost())) {
                    blocked++;
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sc = r.getUrl().getScheme();
                if ("mailto".equals(sc) || "tel".equals(sc) || "sms".equals(sc) || "market".equals(sc)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception e) { }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView v, String u, Bitmap f) {
                pb.setVisibility(View.VISIBLE);
                if (!url.hasFocus()) url.setText(u);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                pb.setVisibility(View.INVISIBLE);
                if (!url.hasFocus()) url.setText(u);
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) { pb.setProgress(p); }

            @Override
            public void onShowCustomView(View v, CustomViewCallback c) {
                if (cv != null) { c.onCustomViewHidden(); return; }
                cv = v;
                cb = c;
                root.setVisibility(View.GONE);
                frame.addView(v, new FrameLayout.LayoutParams(-1, -1));
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }

            @Override
            public void onHideCustomView() {
                if (cv == null) return;
                frame.removeView(cv);
                cv = null;
                cb.onCustomViewHidden();
                root.setVisibility(View.VISIBLE);
                getWindow().getDecorView().setSystemUiVisibility(0);
            }
        });

        w.setDownloadListener((u, ua, cd, mt, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(u));
                r.setMimeType(mt);
                r.addRequestHeader("User-Agent", ua);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(u, cd, mt));
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "Downloading", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
            }
        });

        Uri d = getIntent().getData();
        w.loadUrl(d != null ? d.toString() : HOME);
        root.requestFocus();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.getData() != null) w.loadUrl(i.getData().toString());
    }

    void applyUA() { w.getSettings().setUserAgentString(desktop ? DESK : mobUA); }

    void go(String s) {
        s = s.trim();
        if (s.isEmpty()) return;
        String u;
        if (s.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) u = s;
        else if (s.contains(".") && !s.contains(" ")) u = "https://" + s;
        else u = "https://duckduckgo.com/?q=" + Uri.encode(s);
        url.clearFocus();
        root.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(url.getWindowToken(), 0);
        w.loadUrl(u);
    }

    void menu(View a) {
        PopupMenu m = new PopupMenu(this, a);
        m.getMenu().add(0, 1, 0, "Quiet mode: " + (quiet ? "on" : "off") + " (" + blocked + " blocked)");
        m.getMenu().add(0, 2, 1, "Desktop site: " + (desktop ? "on" : "off"));
        m.getMenu().add(0, 3, 2, "Home");
        m.getMenu().add(0, 4, 3, "Share link");
        m.getMenu().add(0, 5, 4, "Clear history and data");
        m.setOnMenuItemClickListener(i -> {
            switch (i.getItemId()) {
                case 1:
                    quiet = !quiet;
                    sp.edit().putBoolean("quiet", quiet).apply();
                    w.reload();
                    break;
                case 2:
                    desktop = !desktop;
                    sp.edit().putBoolean("desk", desktop).apply();
                    applyUA();
                    w.reload();
                    break;
                case 3:
                    w.loadUrl(HOME);
                    break;
                case 4:
                    Intent t = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, w.getUrl());
                    startActivity(Intent.createChooser(t, "Share link"));
                    break;
                default:
                    w.clearCache(true);
                    w.clearHistory();
                    CookieManager.getInstance().removeAllCookies(null);
                    WebStorage.getInstance().deleteAllData();
                    Toast.makeText(this, "History and data cleared", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        m.show();
    }

    @Override
    public void onBackPressed() {
        if (cv != null) { w.getWebChromeClient().onHideCustomView(); return; }
        if (w.canGoBack()) w.goBack(); else super.onBackPressed();
    }

    @Override protected void onResume() { super.onResume(); w.onResume(); }
    @Override protected void onPause() { w.onPause(); super.onPause(); }
    @Override protected void onDestroy() { w.destroy(); super.onDestroy(); }
}
