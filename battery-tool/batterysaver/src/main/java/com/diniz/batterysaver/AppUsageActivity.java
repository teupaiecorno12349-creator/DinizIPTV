package com.diniz.batterysaver;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppUsageActivity extends AppCompatActivity {

    private final List<AppUse> apps = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(8, 17, 10));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root);

        TextView title = text("APPS MAIS ATIVOS", 25, true, Color.rgb(114, 231, 131));
        root.addView(title);
        TextView subtitle = text("Últimas 24 horas • ordenado pelo tempo em primeiro plano. Isto não é uma medição direta de mAh.", 13, false, Color.rgb(190, 202, 193));
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        MaterialButton dataSaver = button("Abrir Economia de dados / Data Saver", v -> openAction("android.settings.DATA_SAVER_SETTINGS"));
        root.addView(dataSaver);

        MaterialButton optimizations = button("Ver otimizações de bateria", v -> openAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        root.addView(optimizations);

        loadUsage();
        if (apps.isEmpty()) {
            TextView empty = text("Nenhuma estatística disponível. Verifique se o acesso ao uso foi autorizado e volte para esta tela.", 14, false, Color.WHITE);
            empty.setPadding(0, dp(18), 0, 0);
            root.addView(empty);
            return scroll;
        }

        TextView count = text("Exibindo até " + Math.min(30, apps.size()) + " aplicativos", 13, true, Color.WHITE);
        count.setPadding(0, dp(18), 0, dp(8));
        root.addView(count);

        int limit = Math.min(30, apps.size());
        for (int i = 0; i < limit; i++) {
            addAppCard(root, apps.get(i), i + 1);
        }
        return scroll;
    }

    private void loadUsage() {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return;
        long end = System.currentTimeMillis();
        long begin = end - 24L * 60L * 60L * 1000L;
        List<UsageStats> raw = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end);
        if (raw == null || raw.isEmpty()) return;

        Map<String, Long> totals = new HashMap<>();
        for (UsageStats stat : raw) {
            if (stat == null || stat.getPackageName() == null) continue;
            long time = stat.getTotalTimeInForeground();
            if (time <= 0) continue;
            totals.put(stat.getPackageName(), totals.getOrDefault(stat.getPackageName(), 0L) + time);
        }

        PackageManager pm = getPackageManager();
        for (Map.Entry<String, Long> e : totals.entrySet()) {
            if (e.getKey().equals(getPackageName())) continue;
            if (e.getValue() < 60_000L) continue;
            String label = e.getKey();
            try {
                ApplicationInfo info = pm.getApplicationInfo(e.getKey(), 0);
                CharSequence cs = pm.getApplicationLabel(info);
                if (cs != null && cs.length() > 0) label = cs.toString();
            } catch (Exception ignored) { }
            apps.add(new AppUse(label, e.getKey(), e.getValue()));
        }

        Collections.sort(apps, (a, b) -> Long.compare(b.foregroundMs, a.foregroundMs));
    }

    private void addAppCard(LinearLayout root, AppUse app, int position) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(17, 31, 20));
        card.setRadius(dp(15));
        card.setStrokeColor(Color.rgb(52, 92, 59));
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.addView(text(position + ". " + app.label, 16, true, Color.WHITE));
        TextView pkg = text(app.packageName, 11, false, Color.GRAY);
        pkg.setPadding(0, dp(2), 0, 0);
        box.addView(pkg);
        TextView duration = text("Ativo em primeiro plano: " + formatDuration(app.foregroundMs), 13, false, Color.rgb(139, 234, 151));
        duration.setPadding(0, dp(6), 0, dp(6));
        box.addView(duration);

        box.addView(button("Restringir dados em segundo plano", v -> openBackgroundData(app.packageName)));
        box.addView(button("Abrir informações / bateria do app", v -> openAppInfo(app.packageName)));
        card.addView(box);
        root.addView(card);
    }

    private MaterialButton button(String label, View.OnClickListener click) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setCornerRadius(dp(12));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(29, 48, 33)));
        b.setTextColor(Color.WHITE);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void openBackgroundData(String packageName) {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:" + packageName));
            startActivity(i);
        } catch (Exception e) {
            openAppInfo(packageName);
        }
    }

    private void openAppInfo(String packageName) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir os ajustes deste app.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAction(String action) {
        try { startActivity(new Intent(action)); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private String formatDuration(long ms) {
        long minutes = ms / 60_000L;
        long hours = minutes / 60L;
        minutes %= 60L;
        if (hours > 0) return String.format(Locale.getDefault(), "%dh %02dmin", hours, minutes);
        return minutes + " min";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class AppUse {
        final String label;
        final String packageName;
        final long foregroundMs;
        AppUse(String label, String packageName, long foregroundMs) {
            this.label = label;
            this.packageName = packageName;
            this.foregroundMs = foregroundMs;
        }
    }
}
