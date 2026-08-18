package com.diniz.batterysaver;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "super_economia_prefs";
    private static final String SAVED = "saved_originals";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView permissionStatus;
    private TextView batteryStatus;
    private TextView performanceStatus;
    private SharedPreferences prefs;

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
        scroll.setBackgroundColor(Color.rgb(9, 11, 16));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        scroll.addView(root);

        TextView title = text("SUPER ECONOMIA", 27, true, Color.rgb(124, 255, 138));
        root.addView(title);
        TextView subtitle = text("Economia agressiva sem mexer em CPU, GPU, FPS ou frequência do processador.", 15, false, Color.LTGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle);

        performanceStatus = card(root, "Proteção de desempenho", "Verificando...");
        batteryStatus = card(root, "Bateria agora", "Lendo sensores...");
        permissionStatus = card(root, "Permissão de ajustes", "Verificando...");

        root.addView(button("1. Liberar ajustes do sistema", v -> requestWriteSettings()));
        root.addView(button("APLICAR SUPER ECONOMIA", v -> applySuperSaver()));
        root.addView(button("Restaurar minhas configurações", v -> restoreOriginals()));

        TextView what = text("O que o perfil altera", 18, true, Color.WHITE);
        what.setPadding(0, dp(18), 0, dp(8));
        root.addView(what);
        root.addView(text("• brilho em ~27%\n• brilho automático desligado enquanto o perfil estiver ativo\n• tela apaga em 30 segundos\n• vibração de toque desligada\n• sons de toque do sistema desligados\n• rotação automática desligada\n\nNão ativa o modo Economia de bateria do Android e não altera CPU/GPU.", 14, false, Color.LTGRAY));

        TextView quick = text("Atalhos para economizar ainda mais", 18, true, Color.WHITE);
        quick.setPadding(0, dp(20), 0, dp(8));
        root.addView(quick);
        root.addView(button("Wi‑Fi", v -> openPanel(Settings.Panel.ACTION_WIFI)));
        root.addView(button("Internet / dados móveis", v -> openPanel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)));
        root.addView(button("Localização", v -> openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        root.addView(button("Sincronização de contas", v -> openSettings(Settings.ACTION_SYNC_SETTINGS)));
        root.addView(button("Uso da bateria por aplicativo", v -> openIntentAction(Intent.ACTION_POWER_USAGE_SUMMARY)));
        root.addView(button("Modo economia nativo (somente conferir)", v -> openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)));

        TextView note = text("Importante: Android 10 não permite que um APK comum desligue Wi‑Fi/dados móveis ou restrinja à força todos os outros apps sozinho. Por isso esses controles abrem o painel oficial do sistema. O app não fica rodando escondido depois que você fecha.", 13, false, Color.GRAY);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);
        return scroll;
    }

    private TextView card(LinearLayout root, String heading, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackgroundColor(Color.rgb(22, 26, 34));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        box.setLayoutParams(lp);
        TextView h = text(heading, 13, true, Color.rgb(124, 255, 138));
        TextView v = text(value, 15, false, Color.WHITE);
        v.setPadding(0, dp(4), 0, 0);
        box.addView(h);
        box.addView(v);
        root.addView(box);
        return v;
    }

    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void requestWriteSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            openSettings(Settings.ACTION_SETTINGS);
        }
    }

    private void saveOriginalsIfNeeded() {
        if (prefs.getBoolean(SAVED, false)) return;
        ContentResolver cr = getContentResolver();
        SharedPreferences.Editor e = prefs.edit();
        e.putInt("timeout", getSystemInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60000));
        e.putInt("brightness", getSystemInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128));
        e.putInt("brightness_mode", getSystemInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC));
        e.putInt("haptic", getSystemInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1));
        e.putInt("sounds", getSystemInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, 1));
        e.putInt("rotation", getSystemInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1));
        e.putBoolean(SAVED, true).apply();
    }

    private int getSystemInt(ContentResolver cr, String key, int fallback) {
        try { return Settings.System.getInt(cr, key); }
        catch (Exception e) { return fallback; }
    }

    private void applySuperSaver() {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Primeiro permita 'Modificar configurações do sistema'.", Toast.LENGTH_LONG).show();
            requestWriteSettings();
            return;
        }
        saveOriginalsIfNeeded();
        ContentResolver cr = getContentResolver();
        boolean ok = true;
        try {
            ok &= Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            ok &= Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 70);
            ok &= Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000);
            ok &= Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
            ok &= Settings.System.putInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, 0);
            ok &= Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0);
        } catch (Exception e) { ok = false; }
        Toast.makeText(this, ok ? "Super Economia aplicada. CPU/GPU não foram alteradas." : "Algum ajuste foi bloqueado pelo sistema.", Toast.LENGTH_LONG).show();
        refreshStatus();
    }

    private void restoreOriginals() {
        if (!prefs.getBoolean(SAVED, false)) {
            Toast.makeText(this, "Nenhuma configuração original salva ainda.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.System.canWrite(this)) {
            requestWriteSettings();
            return;
        }
        ContentResolver cr = getContentResolver();
        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, prefs.getInt("timeout", 60000));
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, prefs.getInt("brightness", 128));
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, prefs.getInt("brightness_mode", Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC));
        Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, prefs.getInt("haptic", 1));
        Settings.System.putInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, prefs.getInt("sounds", 1));
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, prefs.getInt("rotation", 1));
        prefs.edit().putBoolean(SAVED, false).apply();
        Toast.makeText(this, "Configurações restauradas.", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean canWrite = Settings.System.canWrite(this);
        if (permissionStatus != null) permissionStatus.setText(canWrite ? "Liberada — o perfil pode ser aplicado." : "Pendente — toque no primeiro botão e permita o acesso.");

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean nativeSaver = pm != null && pm.isPowerSaveMode();
        if (performanceStatus != null) performanceStatus.setText(nativeSaver
                ? "Economia nativa está ATIVA. Se quiser desempenho máximo, confira e desative no atalho abaixo. Este APK não mexe em CPU/GPU."
                : "Economia nativa está DESATIVADA. Este APK não modifica CPU, GPU, clocks ou FPS.");

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
        long currentUa = bm != null ? bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) : 0;
        double currentMa = currentUa == Long.MIN_VALUE ? 0 : currentUa / 1000.0;
        String current = Math.abs(currentMa) > 1 ? String.format(Locale.getDefault(), "%.0f mA", currentMa) : "indisponível";
        batteryStatus.setText(String.format(Locale.getDefault(), "%d%% • %.1f °C • %d mV • %s • corrente %s", pct, temp, voltage, charging ? "carregando" : "na bateria", current));
    }

    private void openPanel(String action) { openIntentAction(action); }
    private void openSettings(String action) { openIntentAction(action); }
    private void openIntentAction(String action) {
        try { startActivity(new Intent(action)); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
