package com.diniz.batterysaver;

import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "super_economia_v2_prefs";
    private static final String SAVED = "saved_originals";

    private SharedPreferences prefs;
    private TextView permissionStatus;
    private TextView usageStatus;
    private TextView batteryStatus;
    private TextView performanceStatus;
    private TextView profileStatus;
    private Slider brightnessSlider;
    private SwitchMaterial hapticSwitch;
    private SwitchMaterial soundSwitch;
    private SwitchMaterial rotationSwitch;
    private SwitchMaterial autoBrightnessSwitch;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(updater);
        handler.post(updater);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(updater);
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 17, 10));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root);

        TextView title = text("SUPER ECONOMIA V2", 28, true, Color.rgb(114, 231, 131));
        root.addView(title);
        TextView subtitle = text("Economia agressiva sem reduzir CPU, GPU, clocks ou FPS.", 15, false, Color.rgb(205, 214, 207));
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        performanceStatus = statusCard(root, "DESEMPENHO", "Verificando...");
        batteryStatus = statusCard(root, "BATERIA AGORA", "Lendo sensores...");
        permissionStatus = statusCard(root, "AJUSTES DO SISTEMA", "Verificando...");
        usageStatus = statusCard(root, "ACESSO AO USO", "Verificando...");
        profileStatus = statusCard(root, "PERFIL", "Ainda não aplicado nesta sessão.");

        section(root, "Perfil principal");
        root.addView(text("Brilho do perfil", 14, true, Color.WHITE));
        brightnessSlider = new Slider(this);
        brightnessSlider.setValueFrom(10f);
        brightnessSlider.setValueTo(50f);
        brightnessSlider.setStepSize(1f);
        brightnessSlider.setValue(prefs.getFloat("profile_brightness", 22f));
        root.addView(brightnessSlider);
        TextView sliderHint = text("10% = máxima economia de tela • 50% = mais visibilidade", 12, false, Color.GRAY);
        root.addView(sliderHint);

        autoBrightnessSwitch = toggle("Desligar brilho automático", true);
        hapticSwitch = toggle("Desligar vibração de toque", true);
        soundSwitch = toggle("Desligar sons de toque do sistema", true);
        rotationSwitch = toggle("Desligar rotação automática", true);
        root.addView(autoBrightnessSwitch);
        root.addView(hapticSwitch);
        root.addView(soundSwitch);
        root.addView(rotationSwitch);

        root.addView(primaryButton("APLICAR SUPER ECONOMIA V2", v -> applyProfile()));
        root.addView(secondaryButton("Restaurar minhas configurações", v -> restoreOriginals()));

        section(root, "Permissões e análise");
        root.addView(secondaryButton("Permitir modificar ajustes do sistema", v -> requestWriteSettings()));
        root.addView(secondaryButton("Permitir acesso às estatísticas de uso", v -> requestUsageAccess()));
        root.addView(primaryButton("Ver apps mais ativos nas últimas 24h", v -> openUsageAnalyzer()));

        section(root, "Central de economia sem perda de FPS");
        root.addView(secondaryButton("Economia de dados / Data Saver", v -> openIntentAction("android.settings.DATA_SAVER_SETTINGS")));
        root.addView(secondaryButton("Wi‑Fi", v -> openWifiPanel()));
        root.addView(secondaryButton("Internet / dados móveis", v -> openInternetPanel()));
        root.addView(secondaryButton("Localização", v -> openIntentAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        root.addView(secondaryButton("Sincronização de contas", v -> openIntentAction(Settings.ACTION_SYNC_SETTINGS)));
        root.addView(secondaryButton("Otimização de bateria por aplicativo", v -> openIntentAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        root.addView(secondaryButton("Uso da bateria do Android", v -> openIntentAction(Intent.ACTION_POWER_USAGE_SUMMARY)));
        root.addView(secondaryButton("Conferir economia nativa", v -> openIntentAction(Settings.ACTION_BATTERY_SAVER_SETTINGS)));

        section(root, "O que esta V2 não faz");
        TextView note = text(
                "• não reduz frequência de CPU/GPU\n" +
                "• não ativa o Battery Saver nativo\n" +
                "• não usa serviço permanente em segundo plano\n" +
                "• não promete fechar/restringir outros apps sem autorização do Android\n\n" +
                "No Android 10, Wi‑Fi e várias restrições de outros aplicativos precisam passar pelas telas oficiais do sistema. A V2 organiza esses controles e mostra quais apps realmente ficaram mais ativos.",
                13, false, Color.rgb(165, 177, 168));
        root.addView(note);

        return scroll;
    }

    private TextView statusCard(LinearLayout root, String heading, String value) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(17, 31, 20));
        card.setRadius(dp(16));
        card.setStrokeColor(Color.rgb(52, 92, 59));
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView h = text(heading, 12, true, Color.rgb(114, 231, 131));
        TextView v = text(value, 15, false, Color.WHITE);
        v.setPadding(0, dp(4), 0, 0);
        box.addView(h);
        box.addView(v);
        card.addView(box);
        root.addView(card);
        return v;
    }

    private void section(LinearLayout root, String label) {
        TextView t = text(label, 19, true, Color.WHITE);
        t.setPadding(0, dp(18), 0, dp(8));
        root.addView(t);
    }

    private SwitchMaterial toggle(String label, boolean checked) {
        SwitchMaterial s = new SwitchMaterial(this);
        s.setText(label);
        s.setTextColor(Color.WHITE);
        s.setTextSize(14);
        s.setChecked(checked);
        s.setPadding(0, dp(3), 0, dp(3));
        return s;
    }

    private MaterialButton primaryButton(String label, View.OnClickListener listener) {
        MaterialButton b = buttonBase(label, listener);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(81, 188, 96)));
        b.setTextColor(Color.BLACK);
        return b;
    }

    private MaterialButton secondaryButton(String label, View.OnClickListener listener) {
        MaterialButton b = buttonBase(label, listener);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(29, 48, 33)));
        b.setTextColor(Color.WHITE);
        b.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.rgb(68, 108, 75)));
        b.setStrokeWidth(dp(1));
        return b;
    }

    private MaterialButton buttonBase(String label, View.OnClickListener listener) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setCornerRadius(dp(14));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void requestWriteSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            openIntentAction(Settings.ACTION_SETTINGS);
        }
    }

    private void requestUsageAccess() {
        openIntentAction(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    private void openUsageAnalyzer() {
        if (!hasUsageAccess()) {
            Toast.makeText(this, "Autorize 'Acesso ao uso' e volte para a V2.", Toast.LENGTH_LONG).show();
            requestUsageAccess();
            return;
        }
        startActivity(new Intent(this, AppUsageActivity.class));
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void saveOriginalsIfNeeded() {
        if (prefs.getBoolean(SAVED, false)) return;
        ContentResolver cr = getContentResolver();
        prefs.edit()
                .putInt("timeout", getSystemInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60000))
                .putInt("brightness", getSystemInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
                .putInt("brightness_mode", getSystemInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC))
                .putInt("haptic", getSystemInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1))
                .putInt("sounds", getSystemInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, 1))
                .putInt("rotation", getSystemInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1))
                .putBoolean(SAVED, true)
                .apply();
    }

    private int getSystemInt(ContentResolver cr, String key, int fallback) {
        try { return Settings.System.getInt(cr, key); }
        catch (Exception e) { return fallback; }
    }

    private void applyProfile() {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Primeiro autorize 'Modificar configurações do sistema'.", Toast.LENGTH_LONG).show();
            requestWriteSettings();
            return;
        }

        saveOriginalsIfNeeded();
        int percent = Math.round(brightnessSlider.getValue());
        int brightness = Math.max(1, Math.round(percent * 255f / 100f));
        ContentResolver cr = getContentResolver();
        boolean ok = true;
        try {
            if (autoBrightnessSwitch.isChecked()) {
                ok &= Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            }
            ok &= Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, brightness);
            ok &= Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 45000);
            if (hapticSwitch.isChecked()) ok &= Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
            if (soundSwitch.isChecked()) ok &= Settings.System.putInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, 0);
            if (rotationSwitch.isChecked()) ok &= Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0);
        } catch (Exception e) {
            ok = false;
        }

        prefs.edit().putFloat("profile_brightness", percent).apply();
        profileStatus.setText(ok
                ? "ATIVO • brilho " + percent + "% • CPU/GPU intactas"
                : "Parcialmente aplicado: algum ajuste foi bloqueado pelo sistema.");
        Toast.makeText(this,
                ok ? "V2 aplicada sem reduzir CPU/GPU/FPS." : "Algum ajuste foi bloqueado pelo Android.",
                Toast.LENGTH_LONG).show();
        refreshStatus();
    }

    private void restoreOriginals() {
        if (!prefs.getBoolean(SAVED, false)) {
            Toast.makeText(this, "Nenhuma configuração original foi salva ainda.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.System.canWrite(this)) {
            requestWriteSettings();
            return;
        }
        ContentResolver cr = getContentResolver();
        try {
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, prefs.getInt("timeout", 60000));
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, prefs.getInt("brightness", 128));
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    prefs.getInt("brightness_mode", Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC));
            Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, prefs.getInt("haptic", 1));
            Settings.System.putInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, prefs.getInt("sounds", 1));
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, prefs.getInt("rotation", 1));
            prefs.edit().putBoolean(SAVED, false).apply();
            profileStatus.setText("DESATIVADO • configurações originais restauradas");
            Toast.makeText(this, "Configurações originais restauradas.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "O Android bloqueou parte da restauração.", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshStatus() {
        if (permissionStatus != null) {
            permissionStatus.setText(Settings.System.canWrite(this)
                    ? "LIBERADO • V2 pode alterar brilho e ajustes permitidos."
                    : "PENDENTE • autorize para aplicar o perfil.");
        }
        if (usageStatus != null) {
            usageStatus.setText(hasUsageAccess()
                    ? "LIBERADO • análise dos apps mais ativos disponível."
                    : "PENDENTE • necessário para analisar uso dos aplicativos.");
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean saver = pm != null && pm.isPowerSaveMode();
        if (performanceStatus != null) {
            performanceStatus.setText(saver
                    ? "Battery Saver nativo está ATIVO no sistema. A V2 não o ativou; para desempenho máximo, confira e desative."
                    : "100% PRESERVADO PELO APP • V2 não altera CPU, GPU, clock ou FPS.");
        }

        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat == null || batteryStatus == null) return;
        int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? Math.round(level * 100f / scale) : -1;
        float temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        int voltage = bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int status = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        long currentUa = bm != null ? bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) : Long.MIN_VALUE;
        String current = "indisponível";
        if (currentUa != Long.MIN_VALUE && currentUa != 0) {
            current = String.format(Locale.getDefault(), "%.0f mA", currentUa / 1000.0);
        }
        batteryStatus.setText(String.format(Locale.getDefault(),
                "%d%% • %.1f °C • %d mV • %s • corrente %s",
                pct, temp, voltage, charging ? "carregando" : "na bateria", current));
    }

    private void openWifiPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openIntentAction(Settings.Panel.ACTION_WIFI);
        } else {
            openIntentAction(Settings.ACTION_WIFI_SETTINGS);
        }
    }

    private void openInternetPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openIntentAction(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
        } else {
            openIntentAction(Settings.ACTION_WIRELESS_SETTINGS);
        }
    }

    private void openIntentAction(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) { }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
