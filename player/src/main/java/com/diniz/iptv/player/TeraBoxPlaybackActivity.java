package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TeraBoxPlaybackActivity extends Activity {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "terabox.com", "www.terabox.com",
            "1024terabox.com", "www.1024terabox.com",
            "terabox.app", "www.terabox.app",
            "teraboxshare.com", "www.teraboxshare.com",
            "teraboxlink.com", "www.teraboxlink.com",
            "terasharefile.com", "www.terasharefile.com",
            "terafileshare.com", "www.terafileshare.com",
            "terasharelink.com", "www.terasharelink.com",
            "teraboxapp.com", "www.teraboxapp.com",
            "1024tera.com", "www.1024tera.com"
    ));

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progress;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private final List<String> candidates = new ArrayList<>();
    private int candidateIndex = 0;
    private boolean finishedMainFrame = false;

    public static boolean supports(String url) {
        if (url == null) return false;
        try {
            URI u = URI.create(url.trim());
            String h = u.getHost();
            if (h == null) return false;
            h = h.toLowerCase();
            if (HOSTS.contains(h)) return true;
            for (String host : HOSTS) if (h.endsWith("." + host)) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        immersive();

        String url = getIntent().getStringExtra("url");
        if (!supports(url)) {
            Toast.makeText(this, "Link TeraBox inválido.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        candidates.addAll(buildCandidates(url));

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(56), dp(56));
        pp.gravity = android.view.Gravity.CENTER;
        root.addView(progress, pp);
        setContentView(root);

        configureWebView();
        loadCandidate(0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Keep the platform's normal WebView user agent. Some video sites reject custom TV/user-agent suffixes.
        s.setUserAgentString(WebSettings.getDefaultUserAgent(this));
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                finishedMainFrame = false;
                if (progress != null) progress.setVisibility(View.VISIBLE);
            }

            @Override public void onPageFinished(WebView view, String url) {
                finishedMainFrame = true;
                if (progress != null) progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                injectTvHelpers();
                view.requestFocus();
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl().toString());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) fallbackOrShowError();
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) fallbackOrShowError();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                if (progress != null) progress.setVisibility(newProgress >= 90 ? View.GONE : View.VISIBLE);
            }

            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                view.requestFocus();
                immersive();
            }

            @Override public void onHideCustomView() {
                hideCustomView();
            }

            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView child = new WebView(TeraBoxPlaybackActivity.this);
                WebSettings cs = child.getSettings();
                cs.setJavaScriptEnabled(true);
                cs.setDomStorageEnabled(true);
                cs.setUserAgentString(WebSettings.getDefaultUserAgent(TeraBoxPlaybackActivity.this));
                child.setWebViewClient(new WebViewClient() {
                    private boolean moved;
                    private void move(String url) {
                        if (moved || url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
                        moved = true;
                        webView.loadUrl(url);
                        child.stopLoading();
                        child.destroy();
                    }
                    @Override public void onPageStarted(WebView v, String url, Bitmap favicon) { move(url); }
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { move(r.getUrl().toString()); return true; }
                    @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { move(url); return true; }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(child);
                resultMsg.sendToTarget();
                return true;
            }
        });

        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP || customView != null) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                webView.evaluateJavascript(
                        "(function(){" +
                        "var a=document.activeElement;" +
                        "if(a&&a!==document.body&&typeof a.click==='function'){a.click();return 'focused';}" +
                        "var v=document.querySelector('video');if(v){v.controls=true;var p=v.play();return 'video';}" +
                        "var b=document.querySelector('button,[role=button],.play,.play-btn,.video-play');if(b){b.click();return 'button';}" +
                        "return 'none';})()", null);
                return true;
            }
            return false;
        });
    }

    private boolean handleNavigation(String next) {
        if (next == null) return true;
        if (next.startsWith("http://") || next.startsWith("https://")) {
            webView.loadUrl(next);
            return true;
        }
        if (next.startsWith("intent://")) {
            try {
                Intent intent = Intent.parseUri(next, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null && (fallback.startsWith("http://") || fallback.startsWith("https://"))) webView.loadUrl(fallback);
            } catch (Exception ignored) {}
            return true;
        }
        // Do not leave the TV app for terabox://, market:// or other external schemes.
        return true;
    }

    private void injectTvHelpers() {
        String js = "(function(){" +
                "try{window.open=function(u){if(u)location.href=u;return window;};}catch(e){}" +
                "document.querySelectorAll('video').forEach(function(v){v.setAttribute('controls','controls');v.controls=true;v.style.maxWidth='100vw';v.style.maxHeight='100vh';});" +
                "var f=document.querySelector('video,button,[role=button],a');if(f&&f.focus)f.focus();" +
                "})()";
        webView.evaluateJavascript(js, null);
    }

    private List<String> buildCandidates(String original) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(original.trim());
        try {
            URI u = URI.create(original.trim());
            String path = u.getRawPath() == null ? "/" : u.getRawPath();
            String suffix = path;
            if (u.getRawQuery() != null && !u.getRawQuery().isEmpty()) suffix += "?" + u.getRawQuery();
            if (u.getRawFragment() != null && !u.getRawFragment().isEmpty()) suffix += "#" + u.getRawFragment();
            if (path.startsWith("/s/") || path.startsWith("/sharing/")) {
                out.add("https://www.terabox.com" + suffix);
                out.add("https://www.1024terabox.com" + suffix);
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(out);
    }

    private void loadCandidate(int index) {
        if (index < 0 || index >= candidates.size()) return;
        candidateIndex = index;
        finishedMainFrame = false;
        webView.loadUrl(candidates.get(index));
        webView.requestFocus();
    }

    private void fallbackOrShowError() {
        if (finishedMainFrame) return;
        int next = candidateIndex + 1;
        if (next < candidates.size()) {
            loadCandidate(next);
        } else {
            if (progress != null) progress.setVisibility(View.GONE);
            Toast.makeText(this, "O TeraBox não conseguiu abrir este compartilhamento nesta TV Box.", Toast.LENGTH_LONG).show();
        }
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        webView.requestFocus();
        immersive();
    }

    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.requestFocus();
        }
        immersive();
    }

    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
