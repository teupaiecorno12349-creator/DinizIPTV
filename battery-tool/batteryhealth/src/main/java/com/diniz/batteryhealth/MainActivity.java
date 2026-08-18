package com.diniz.batteryhealth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 17, 20);
    private static final int CARD = Color.rgb(27, 29, 34);
    private static final int TEXT = Color.rgb(242, 244, 248);
    private static final int MUTED = Color.rgb(170, 176, 187);
    private static final int ACCENT = Color.rgb(106, 181, 255);
    private static final int GOOD = Color.rgb(102, 214, 145);
    private static final int WARN = Color.rgb(255, 196, 91);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BatteryManager batteryManager;
    private SharedPreferences prefs;

    private TextView healthBig;
    private TextView healthDetail;
    private TextView capacityText;
    private TextView cycleText;
    private TextView ageText;
    private TextView levelText;
    private TextView tempText;
    private TextView voltageText;
    private TextView currentText;
    private TextView statusText;
    private TextView remainingText;
    private TextView uptimeText;
    private TextView technologyText;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshBattery();
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        prefs = getSharedPreferences("battery_health", MODE_PRIVATE);
        if (!prefs.contains("first_seen_ms")) {
            prefs.edit().putLong("first_seen_ms", System.currentTimeMillis()).apply();
        }

        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Saúde da Bateria", 28, TEXT, Typeface.BOLD);
        root.addView(title);
        TextView sub = text("Leitura do Android + controlador da bateria", 14, MUTED, Typeface.NORMAL);
        sub.setPadding(0, dp(3), 0, dp(16));
        root.addView(sub);

        LinearLayout healthCard = card();
        root.addView(healthCard, cardParams());
        TextView healthLabel = text("SAÚDE DA BATERIA", 12, MUTED, Typeface.BOLD);
        healthLabel.setLetterSpacing(0.12f);
        healthCard.addView(healthLabel);
        healthBig = text("Lendo…", 38, TEXT, Typeface.BOLD);
        healthBig.setPadding(0, dp(5), 0, dp(2));
        healthCard.addView(healthBig);
        healthDetail = text("", 14, MUTED, Typeface.NORMAL);
        healthCard.addView(healthDetail);

        root.addView(section("Capacidade e desgaste"));
        capacityText = valueCard(root, "Capacidade", "Lendo…");
        cycleText = valueCard(root, "Ciclos de carga", "Lendo…");
        ageText = valueCard(root, "Idade / tempo de uso", "Lendo…");

        root.addView(section("Agora"));
        levelText = valueCard(root, "Carga", "Lendo…");
        statusText = valueCard(root, "Estado Android", "Lendo…");
        tempText = valueCard(root, "Temperatura", "Lendo…");
        voltageText = valueCard(root, "Tensão", "Lendo…");
        currentText = valueCard(root, "Corrente", "Lendo…");
        remainingText = valueCard(root, "Tempo estimado", "Lendo…");
        technologyText = valueCard(root, "Tecnologia", "Lendo…");
        uptimeText = valueCard(root, "Celular ligado há", "Lendo…");

        TextView note = text(
                "Como a leitura funciona:\n\n" +
                "• Saúde medida = capacidade máxima atual ÷ capacidade de projeto, quando o controlador expõe esses valores.\n" +
                "• Se o aparelho esconder esses dados, o app não inventa um percentual; pode mostrar apenas uma estimativa de baixa confiança.\n" +
                "• A idade histórica da bateria só pode ser mostrada exatamente se o firmware expuser a data de primeiro uso/fabricação. Caso contrário, ciclos de carga são o melhor indicador disponível.\n" +
                "• O monitoramento local começa na primeira abertura deste app.",
                13, MUTED, Typeface.NORMAL);
        note.setLineSpacing(0, 1.12f);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);

        return scroll;
    }

    private void refreshBattery() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;

        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        int tempTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        int voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        String technology = battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

        long chargeCounterUaH = safeLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long currentNowUa = safeLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long currentAvgUa = safeLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);

        HealthInfo hi = findHealth(percent, chargeCounterUaH);
        showHealth(hi, health);

        if (percent >= 0) levelText.setText(percent + "%" + (plugged != 0 ? "  •  carregador conectado" : "  •  usando bateria"));
        else levelText.setText("Não disponível");

        statusText.setText(healthName(health) + "  •  " + chargeStatusName(status));
        statusText.setTextColor(health == BatteryManager.BATTERY_HEALTH_GOOD ? GOOD : TEXT);

        if (tempTenths != Integer.MIN_VALUE) {
            float c = tempTenths / 10f;
            tempText.setText(new DecimalFormat("0.0").format(c) + " °C" + temperatureNote(c));
            tempText.setTextColor(c >= 45 ? WARN : TEXT);
        } else tempText.setText("Não disponível");

        voltageText.setText(voltageMv > 0 ? new DecimalFormat("0.000").format(voltageMv / 1000.0) + " V" : "Não disponível");

        long current = validCurrent(currentAvgUa) ? currentAvgUa : currentNowUa;
        if (validCurrent(current)) {
            double ma = current / 1000.0;
            String flow = ma > 0 ? "entrando" : ma < 0 ? "saindo" : "estável";
            String power = "";
            if (voltageMv > 0) {
                double watts = (voltageMv / 1000.0) * (Math.abs(ma) / 1000.0);
                power = "  •  " + new DecimalFormat("0.00").format(watts) + " W";
            }
            currentText.setText(new DecimalFormat("0").format(Math.abs(ma)) + " mA " + flow + power);
        } else currentText.setText("Não exposta pelo sistema");

        technologyText.setText(technology == null || technology.trim().isEmpty() ? "Não informada" : technology);
        remainingText.setText(estimateTime(status, percent, chargeCounterUaH, current, plugged));
        uptimeText.setText(formatDuration(SystemClock.elapsedRealtime()));
        cycleText.setText(readCycles(battery));
        ageText.setText(readBatteryAge());
        capacityText.setText(capacityDescription(hi, percent, chargeCounterUaH));
    }

    private HealthInfo findHealth(int levelPercent, long chargeCounterUaH) {
        HealthInfo out = new HealthInfo();

        Long direct = readFirstLong(new String[]{
                "/sys/class/power_supply/battery/soh",
                "/sys/class/power_supply/bms/soh",
                "/sys/class/power_supply/battery/state_of_health",
                "/sys/class/power_supply/bms/state_of_health"
        });
        if (direct != null && direct > 0 && direct <= 120) {
            out.percent = direct.doubleValue();
            out.measured = true;
            out.source = "SOH informado diretamente pelo controlador";
        }

        CapacityPair pair = findCapacityPair();
        if (pair != null && pair.design > 0 && pair.full > 0) {
            double ratio = 100.0 * pair.full / pair.design;
            if (ratio > 20 && ratio < 130) {
                if (!out.measured) {
                    out.percent = ratio;
                    out.measured = true;
                    out.source = "capacidade máxima atual ÷ capacidade de projeto";
                }
                out.fullRaw = pair.full;
                out.designRaw = pair.design;
                out.capacityUnit = pair.unit;
            }
        }

        double designMah = readDesignCapacityMah();
        if (out.designMah <= 0 && designMah > 0) out.designMah = designMah;

        if (!out.measured && designMah > 0 && chargeCounterUaH > 0 && levelPercent >= 15) {
            double remainingMah = chargeCounterUaH / 1000.0;
            double estimatedFullMah = remainingMah / (levelPercent / 100.0);
            double estimate = 100.0 * estimatedFullMah / designMah;
            if (estimate >= 30 && estimate <= 125) {
                out.percent = estimate;
                out.estimated = true;
                out.source = "estimativa pelo contador de carga atual (baixa confiança)";
                out.estimatedFullMah = estimatedFullMah;
            }
        }
        return out;
    }

    private void showHealth(HealthInfo hi, int androidHealth) {
        if (hi.measured) {
            healthBig.setText(new DecimalFormat("0.0").format(hi.percent) + "%");
            healthBig.setTextColor(hi.percent >= 80 ? GOOD : hi.percent >= 65 ? WARN : TEXT);
            healthDetail.setText("MEDIDA • " + hi.source + "\nEstado Android: " + healthName(androidHealth));
        } else if (hi.estimated) {
            healthBig.setText("≈ " + new DecimalFormat("0").format(hi.percent) + "%");
            healthBig.setTextColor(WARN);
            healthDetail.setText("ESTIMATIVA • " + hi.source + "\nO aparelho não expôs um SOH medido diretamente.");
        } else {
            healthBig.setText("Não exposta");
            healthBig.setTextColor(TEXT);
            healthDetail.setText("O controlador deste aparelho não forneceu capacidade máxima/projeto acessível sem root.\nEstado Android: " + healthName(androidHealth));
        }
    }

    private String capacityDescription(HealthInfo hi, int levelPercent, long chargeCounterUaH) {
        StringBuilder b = new StringBuilder();
        if (hi.fullRaw > 0 && hi.designRaw > 0) {
            if ("uAh".equals(hi.capacityUnit)) {
                b.append("Máxima atual: ").append(formatMah(hi.fullRaw)).append(" mAh\n");
                b.append("Projeto: ").append(formatMah(hi.designRaw)).append(" mAh");
            } else {
                b.append("Máxima/projeto lidos do controlador\n");
                b.append("Relação: ").append(new DecimalFormat("0.0").format(100.0 * hi.fullRaw / hi.designRaw)).append("%");
            }
        } else if (hi.designMah > 0) {
            b.append("Projeto detectado: ").append(new DecimalFormat("0").format(hi.designMah)).append(" mAh");
            if (hi.estimatedFullMah > 0) b.append("\nMáxima estimada agora: ≈ ").append(new DecimalFormat("0").format(hi.estimatedFullMah)).append(" mAh");
        } else {
            b.append("Capacidade máxima/projeto não exposta");
        }
        if (chargeCounterUaH > 0) {
            b.append("\nCarga restante no contador: ").append(new DecimalFormat("0").format(chargeCounterUaH / 1000.0)).append(" mAh");
        } else if (levelPercent >= 0) {
            b.append("\nCarga restante: ").append(levelPercent).append("%");
        }
        return b.toString();
    }

    private CapacityPair findCapacityPair() {
        String[][] pairs = new String[][]{
                {"/sys/class/power_supply/battery/charge_full", "/sys/class/power_supply/battery/charge_full_design", "uAh"},
                {"/sys/class/power_supply/bms/charge_full", "/sys/class/power_supply/bms/charge_full_design", "uAh"},
                {"/sys/class/power_supply/battery/energy_full", "/sys/class/power_supply/battery/energy_full_design", "uWh"},
                {"/sys/class/power_supply/bms/energy_full", "/sys/class/power_supply/bms/energy_full_design", "uWh"},
                {"/sys/class/power_supply/battery/full_charge_capacity", "/sys/class/power_supply/battery/design_capacity", "uAh"},
                {"/sys/class/power_supply/bms/full_charge_capacity", "/sys/class/power_supply/bms/design_capacity", "uAh"}
        };
        for (String[] p : pairs) {
            Long full = readLongFile(p[0]);
            Long design = readLongFile(p[1]);
            if (full != null && design != null && full > 0 && design > 0) return new CapacityPair(full, design, p[2]);
        }
        return null;
    }

    private double readDesignCapacityMah() {
        Long design = readFirstLong(new String[]{
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/bms/charge_full_design",
                "/sys/class/power_supply/battery/design_capacity",
                "/sys/class/power_supply/bms/design_capacity"
        });
        if (design != null && design > 0) return normalizeMah(design);

        try {
            int id = getResources().getIdentifier("config_batteryCapacity", "integer", "android");
            if (id != 0) {
                int value = getResources().getInteger(id);
                if (value > 100 && value < 20000) return value;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private String readCycles(Intent battery) {
        int fromIntent = battery.getIntExtra("android.os.extra.CYCLE_COUNT", -1);
        if (fromIntent >= 0) return fromIntent + " ciclos • informado pelo Android";

        Long sys = readFirstLong(new String[]{
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/bms/cycle_count",
                "/sys/class/power_supply/battery/battery_cycle",
                "/sys/class/power_supply/bms/battery_cycle"
        });
        if (sys != null && sys >= 0 && sys < 100000) return sys + " ciclos • informado pelo controlador";
        return "Não exposto pelo sistema neste aparelho";
    }

    private String readBatteryAge() {
        DateInfo firstUse = readDateInfo(new String[]{
                "/sys/class/power_supply/battery/first_usage_date",
                "/sys/class/power_supply/bms/first_usage_date",
                "/sys/class/power_supply/battery/first_use_date",
                "/sys/class/power_supply/bms/first_use_date"
        }, "primeiro uso");
        if (firstUse == null) {
            firstUse = readDateInfo(new String[]{
                    "/sys/class/power_supply/battery/manufacture_date",
                    "/sys/class/power_supply/bms/manufacture_date",
                    "/sys/class/power_supply/battery/manufacturing_date",
                    "/sys/class/power_supply/battery/date_of_manufacture"
            }, "fabricação");
        }

        long firstSeen = prefs.getLong("first_seen_ms", System.currentTimeMillis());
        String monitored = "Monitorada pelo app há " + formatDuration(System.currentTimeMillis() - firstSeen);

        if (firstUse != null && firstUse.millis > 0 && firstUse.millis < System.currentTimeMillis()) {
            long ageMs = System.currentTimeMillis() - firstUse.millis;
            String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(firstUse.millis));
            return formatAge(ageMs) + " • data de " + firstUse.kind + ": " + date + "\n" + monitored;
        }
        return "Idade histórica não exposta pelo firmware\n" + monitored;
    }

    private DateInfo readDateInfo(String[] paths, String kind) {
        for (String path : paths) {
            String raw = readTextFile(path);
            if (raw == null) continue;
            Long ms = parseDateMillis(raw.trim());
            if (ms != null) return new DateInfo(ms, kind);
        }
        return null;
    }

    private Long parseDateMillis(String s) {
        try {
            if (s.matches("\\d{13}")) return Long.parseLong(s);
            if (s.matches("\\d{10}")) return Long.parseLong(s) * 1000L;
            String digits = s.replaceAll("[^0-9]", "");
            if (digits.length() == 8) {
                String[] patterns = {"yyyyMMdd", "ddMMyyyy"};
                for (String pattern : patterns) {
                    try {
                        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
                        f.setLenient(false);
                        Date d = f.parse(digits);
                        if (d != null && d.getTime() > 946684800000L && d.getTime() < System.currentTimeMillis() + 86400000L) return d.getTime();
                    } catch (Exception ignored) {}
                }
            }
            String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy"};
            for (String pattern : patterns) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
                    f.setLenient(false);
                    Date d = f.parse(s);
                    if (d != null && d.getTime() > 946684800000L && d.getTime() < System.currentTimeMillis() + 86400000L) return d.getTime();
                } catch (Exception ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String estimateTime(int status, int percent, long chargeCounterUaH, long currentUa, int plugged) {
        if (plugged != 0 && Build.VERSION.SDK_INT >= 28 && batteryManager != null) {
            try {
                long ms = batteryManager.computeChargeTimeRemaining();
                if (ms > 0) return "Até 100%: ≈ " + formatDuration(ms);
            } catch (Throwable ignored) {}
        }

        if (plugged == 0 && chargeCounterUaH > 0 && validCurrent(currentUa) && currentUa < -20000) {
            double hours = chargeCounterUaH / (double)Math.abs(currentUa);
            long ms = (long)(hours * 3600000L);
            if (ms > 60000 && ms < 7L * 24 * 3600000L) return "Restante: ≈ " + formatDuration(ms) + " • baseado na corrente média";
        }

        if (status == BatteryManager.BATTERY_STATUS_FULL) return "Bateria cheia";
        if (percent >= 0) return "Sem corrente média estável para calcular agora";
        return "Não disponível";
    }

    private long safeLongProperty(int id) {
        if (batteryManager == null) return Long.MIN_VALUE;
        try {
            long v = batteryManager.getLongProperty(id);
            if (v == Long.MIN_VALUE || v == Integer.MIN_VALUE) return Long.MIN_VALUE;
            return v;
        } catch (Throwable ignored) {
            return Long.MIN_VALUE;
        }
    }

    private boolean validCurrent(long v) {
        return v != Long.MIN_VALUE && v != Integer.MIN_VALUE && Math.abs(v) < 20000000L;
    }

    private String healthName(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Boa";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Superaquecida";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Bateria degradada/morta";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Sobretensão";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Falha não especificada";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Muito fria";
            default: return "Desconhecida";
        }
    }

    private String chargeStatusName(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "carregando";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "descarregando";
            case BatteryManager.BATTERY_STATUS_FULL: return "cheia";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "não carregando";
            default: return "estado desconhecido";
        }
    }

    private String temperatureNote(float c) {
        if (c >= 45) return "  •  alta";
        if (c >= 40) return "  •  quente";
        if (c <= 5) return "  •  fria";
        return "  •  normal";
    }

    private Long readFirstLong(String[] paths) {
        for (String p : paths) {
            Long v = readLongFile(p);
            if (v != null) return v;
        }
        return null;
    }

    private Long readLongFile(String path) {
        String s = readTextFile(path);
        if (s == null) return null;
        Matcher m = Pattern.compile("-?\\d+").matcher(s);
        if (!m.find()) return null;
        try { return Long.parseLong(m.group()); } catch (Exception e) { return null; }
    }

    private String readTextFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return null;
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line = br.readLine();
            br.close();
            return line == null ? null : line.trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private double normalizeMah(long raw) {
        if (raw > 20000) return raw / 1000.0;
        return raw;
    }

    private String formatMah(long raw) {
        return new DecimalFormat("0").format(normalizeMah(raw));
    }

    private String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long totalMinutes = ms / 60000L;
        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0) return days + " d " + hours + " h";
        if (hours > 0) return hours + " h " + minutes + " min";
        return minutes + " min";
    }

    private String formatAge(long ms) {
        long days = ms / 86400000L;
        long years = days / 365L;
        long months = (days % 365L) / 30L;
        long remDays = (days % 365L) % 30L;
        if (years > 0) return years + " ano" + (years == 1 ? "" : "s") + " e " + months + " mês" + (months == 1 ? "" : "es");
        if (months > 0) return months + " mês" + (months == 1 ? "" : "es") + " e " + remDays + " dias";
        return days + " dias";
    }

    private TextView valueCard(LinearLayout root, String label, String initial) {
        LinearLayout c = card();
        root.addView(c, smallCardParams());
        TextView l = text(label, 12, MUTED, Typeface.BOLD);
        c.addView(l);
        TextView v = text(initial, 16, TEXT, Typeface.NORMAL);
        v.setPadding(0, dp(5), 0, 0);
        v.setLineSpacing(0, 1.08f);
        c.addView(v);
        return v;
    }

    private TextView section(String s) {
        TextView t = text(s, 17, TEXT, Typeface.BOLD);
        t.setPadding(dp(2), dp(18), 0, dp(4));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        c.setBackground(bg);
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(4));
        return p;
    }

    private LinearLayout.LayoutParams smallCardParams() {
        LinearLayout.LayoutParams p = cardParams();
        p.setMargins(0, dp(6), 0, 0);
        return p;
    }

    private TextView text(String s, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", style));
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static class HealthInfo {
        double percent = -1;
        boolean measured;
        boolean estimated;
        String source = "";
        long fullRaw = -1;
        long designRaw = -1;
        String capacityUnit = "";
        double designMah = -1;
        double estimatedFullMah = -1;
    }

    private static class CapacityPair {
        final long full;
        final long design;
        final String unit;
        CapacityPair(long full, long design, String unit) {
            this.full = full;
            this.design = design;
            this.unit = unit;
        }
    }

    private static class DateInfo {
        final long millis;
        final String kind;
        DateInfo(long millis, String kind) {
            this.millis = millis;
            this.kind = kind;
        }
    }
}
