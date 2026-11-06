package com.diniz.dnschanger;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int VPN_REQUEST_CODE = 4102;
    private static final String PREFS = "diniz_dns";
    private static final String EXTRA_PRIMARY = "primary";
    private static final String EXTRA_SECONDARY = "secondary";
    private static final String EXTRA_NAME = "dns_name";

    private SharedPreferences preferences;
    private TextView statusView;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildInterface();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(20), dp(28), dp(20));
        root.setBackgroundColor(Color.rgb(10, 16, 28));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(28), dp(24), dp(28), dp(24));
        panel.setBackground(rounded(Color.rgb(20, 31, 49), dp(22)));
        panel.setFocusable(false);

        TextView title = new TextView(this);
        title.setText("Diniz DNS");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Escolha o DNS que sua TV Box vai usar");
        subtitle.setTextColor(Color.rgb(190, 205, 225));
        subtitle.setTextSize(17);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(8);
        subParams.bottomMargin = dp(18);
        panel.addView(subtitle, subParams);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(145, 220, 175));
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusView.setBackground(rounded(Color.rgb(13, 23, 38), dp(12)));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.bottomMargin = dp(18);
        panel.addView(statusView, statusParams);

        Button google = createButton("Google DNS  |  8.8.8.8", Color.rgb(38, 105, 190));
        google.setOnClickListener(v -> requestDns("Google DNS", "8.8.8.8", "8.8.4.4"));
        panel.addView(google, buttonParams());

        Button cloudflare = createButton("Cloudflare DNS  |  1.1.1.1", Color.rgb(205, 105, 38));
        cloudflare.setOnClickListener(v -> requestDns("Cloudflare DNS", "1.1.1.1", "1.0.0.1"));
        panel.addView(cloudflare, buttonParams());

        Button automatic = createButton("Desativar e voltar ao DNS automático", Color.rgb(63, 77, 96));
        automatic.setOnClickListener(v -> disableDns());
        panel.addView(automatic, buttonParams());

        TextView explanation = new TextView(this);
        explanation.setText(
                "O app encaminha as consultas DNS diretamente aos servidores oficiais escolhidos. " +
                "Não altera respostas, não cria DNS falso e não envia sua navegação por um servidor VPN externo. " +
                "Na primeira ativação, confirme a solicitação de VPN exibida pelo Android."
        );
        explanation.setTextColor(Color.rgb(172, 185, 203));
        explanation.setTextSize(13);
        explanation.setGravity(Gravity.CENTER);
        explanation.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(16);
        panel.addView(explanation, noteParams);

        root.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        setContentView(root);
        google.requestFocus();
    }

    private Button createButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackground(rounded(color, dp(14)));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setOnFocusChangeListener((view, focused) -> {
            view.setScaleX(focused ? 1.025f : 1.0f);
            view.setScaleY(focused ? 1.025f : 1.0f);
            view.setElevation(focused ? dp(8) : dp(2));
        });
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.height = dp(62);
        params.bottomMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestDns(String name, String primary, String secondary) {
        preferences.edit()
                .putString(EXTRA_NAME, name)
                .putString(EXTRA_PRIMARY, primary)
                .putString(EXTRA_SECONDARY, secondary)
                .apply();

        Intent consent = VpnService.prepare(this);
        if (consent != null) {
            startActivityForResult(consent, VPN_REQUEST_CODE);
        } else {
            activateSelectedDns();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                activateSelectedDns();
            } else {
                statusView.setText("VPN não autorizada. O DNS da rede não foi alterado.");
            }
        }
    }

    private void activateSelectedDns() {
        String name = preferences.getString(EXTRA_NAME, "DNS");
        String primary = preferences.getString(EXTRA_PRIMARY, "1.1.1.1");
        String secondary = preferences.getString(EXTRA_SECONDARY, "1.0.0.1");

        Intent service = new Intent(this, DnsVpnService.class);
        service.setAction(DnsVpnService.ACTION_START);
        service.putExtra(EXTRA_NAME, name);
        service.putExtra(EXTRA_PRIMARY, primary);
        service.putExtra(EXTRA_SECONDARY, secondary);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        statusView.setText("Solicitando ativação de " + name + "...");
        handler.postDelayed(this::refreshStatus, 900);
    }

    private void disableDns() {
        stopService(new Intent(this, DnsVpnService.class));
        preferences.edit().putBoolean("active", false).apply();
        statusView.setText("DNS automático da rede restaurado.");
    }

    private void refreshStatus() {
        if (statusView == null) return;
        boolean active = preferences.getBoolean("active", false);
        if (active) {
            String name = preferences.getString(EXTRA_NAME, "DNS");
            String primary = preferences.getString(EXTRA_PRIMARY, "");
            String secondary = preferences.getString(EXTRA_SECONDARY, "");
            statusView.setText("ATIVO  •  " + name + "\nPrimário: " + primary + "   Secundário: " + secondary);
        } else {
            statusView.setText("DESLIGADO  •  usando o DNS fornecido pela rede");
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
