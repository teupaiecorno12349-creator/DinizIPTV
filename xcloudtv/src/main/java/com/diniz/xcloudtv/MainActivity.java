package com.diniz.xcloudtv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.net.http.SslError;

public final class MainActivity extends Activity {
    private static final String CLOUD_URL = "https://play.xbox.com/";

    private FrameLayout browserFrame;
    private WebView webView;
    private ProgressBar progress;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean inBrowser;
    private String lastUrl = CLOUD_URL;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        showHome();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void showHome() {
        inBrowser = false;
        if (fullscreenView != null) hideFullscreenView();
        if (webView != null) {
            webView.onPause();
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
        }

        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.rgb(7, 12, 20));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(42), dp(22), dp(42), dp(18));

        TextView brand = label("DINIZ X-CLOUD", 30, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        content.addView(brand, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView subtitle = label("Jogos na nuvem para TV Box", 18, Color.rgb(178, 190, 205));
        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView status = label(networkStatus(), 14, Color.rgb(132, 205, 145));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(34));
        statusParams.bottomMargin = dp(18);
        content.addView(status, statusParams);

        TextView launch = label("ABRIR XBOX CLOUD", 20, Color.WHITE);
        launch.setTypeface(Typeface.DEFAULT_BOLD);
        launch.setGravity(Gravity.CENTER);
        launch.setFocusable(true);
        launch.setFocusableInTouchMode(true);
        launch.setClickable(true);
        launch.setBackground(rounded(Color.rgb(16, 124, 16), 14));
        launch.setOnFocusChangeListener((view, focused) -> {
            view.setScaleX(focused ? 1.04f : 1.0f);
            view.setScaleY(focused ? 1.04f : 1.0f);
            view.setBackground(rounded(
                focused ? Color.rgb(25, 158, 35) : Color.rgb(16, 124, 16), 14));
        });
        launch.setOnClickListener(view -> openCloud());
        LinearLayout.LayoutParams launchParams =
            new LinearLayout.LayoutParams(dp(390), dp(72));
        launchParams.bottomMargin = dp(20);
        content.addView(launch, launchParams);

        TextView loginInfo = label(
            "Entre com sua conta Microsoft na página oficial do Xbox.\n" +
            "A sessão fica salva nos dados locais do navegador do aplicativo.",
            15, Color.rgb(190, 198, 210));
        loginInfo.setGravity(Gravity.CENTER);
        content.addView(loginInfo, new LinearLayout.LayoutParams(-1, dp(62)));

        TextView quality = label(
            "Vídeo: aceleração de hardware do Android/WebView.\n" +
            "Resolução e FPS são ajustados pelo serviço Xbox.",
            14, Color.rgb(135, 148, 165));
        quality.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qualityParams = new LinearLayout.LayoutParams(-1, dp(58));
        qualityParams.topMargin = dp(16);
        content.addView(quality, qualityParams);

        TextView controls = label(
            "CONTROLE XBOX: USB ou Bluetooth  •  Navegação: controle remoto",
            13, Color.rgb(115, 130, 148));
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, dp(38));
        controlsParams.topMargin = dp(12);
        content.addView(controls, controlsParams);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
            -1, -2, Gravity.CENTER);
        page.addView(content, contentParams);
        setContentView(page);
        launch.requestFocus();
        enterImmersiveMode();
    }

    private String networkStatus() {
        try {
            ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected()
                ? "● Internet conectada"
                : "● Sem conexão com a internet";
        } catch (Exception ignored) {
            return "Verifique a conexão com a internet";
        }
    }

    private void openCloud() {
        openCloud(null);
    }

    private void openCloud(String requestedUrl) {
        inBrowser = true;
        enterImmersiveMode();
        if (webView == null) createBrowser();

        browserFrame = new FrameLayout(this);
        browserFrame.setBackgroundColor(Color.BLACK);

        if (webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }
        browserFrame.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
            -1, dp(3), Gravity.TOP);
        browserFrame.addView(progress, progressParams);

        setContentView(browserFrame);
        webView.setVisibility(View.VISIBLE);
        webView.onResume();
        webView.requestFocus();

        if (requestedUrl != null && !requestedUrl.isEmpty()) {
            webView.loadUrl(requestedUrl);
        } else if (webView.getUrl() == null || webView.getUrl().isEmpty()) {
            webView.loadUrl(CLOUD_URL);
        }
    }

    private void createBrowser() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                lastUrl = url;
                if (progress != null) {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(0);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                lastUrl = url;
                CookieManager.getInstance().flush();
                if (progress != null) progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(
                WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                        "Falha ao carregar o Xbox Cloud. Verifique a rede e tente novamente.",
                        Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onReceivedSslError(
                WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }

            @Override
            @android.annotation.TargetApi(Build.VERSION_CODES.O)
            public boolean onRenderProcessGone(
                WebView view, RenderProcessGoneDetail detail) {
                String restoreUrl = view.getUrl();
                if (restoreUrl == null || restoreUrl.isEmpty()) restoreUrl = lastUrl;
                if (view.getParent() instanceof ViewGroup) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                view.destroy();
                webView = null;
                createBrowser();
                if (inBrowser) openCloud(restoreUrl);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                if (progress != null) {
                    progress.setProgress(value);
                    progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                if (browserFrame != null) {
                    webView.setVisibility(View.GONE);
                    browserFrame.addView(view, new FrameLayout.LayoutParams(-1, -1));
                }
                enterImmersiveMode();
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenView();
            }
        });
    }

    private void hideFullscreenView() {
        if (fullscreenView == null) return;
        if (browserFrame != null) browserFrame.removeView(fullscreenView);
        fullscreenView = null;
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
            webView.requestFocus();
        }
        enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK &&
            event.getAction() == KeyEvent.ACTION_UP) {
            handleBack();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void handleBack() {
        if (fullscreenView != null) {
            hideFullscreenView();
            return;
        }
        if (inBrowser && webView != null) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                showHome();
            }
            return;
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inBrowser && webView != null) webView.onResume();
        CookieManager.getInstance().flush();
        enterImmersiveMode();
    }

    @Override
    protected void onPause() {
        if (inBrowser && webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
