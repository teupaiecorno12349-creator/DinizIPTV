package com.mgplay.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 8, 18);
    private static final int PANEL = Color.rgb(19, 18, 38);
    private static final int PURPLE = Color.rgb(133, 52, 238);
    private static final int MUTED = Color.rgb(169, 164, 195);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private LinearLayout content;
    private TextView status;
    private ProgressBar progress;
    private String section = "home";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        DeviceIdentity.ensure(this);
        try { startService(new Intent(this, PanelService.class)); } catch (Exception ignored) {}
        showScreen();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String title, boolean selected) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(15), 0, dp(10), 0);
        b.setBackground(background(selected ? PURPLE : Color.rgb(24, 23, 43), 10));
        b.setFocusable(true);
        b.setClickable(true);
        return b;
    }

    private void showScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(28), dp(10), dp(28), dp(10));
        header.setBackgroundColor(Color.rgb(11, 10, 25));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(76)));

        TextView brand = text("MG PLAY", 27, Color.WHITE, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView subtitle = text("SUA TV, DO SEU JEITO", 11, MUTED, true);
        header.addView(subtitle);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(14), dp(18), dp(12), dp(18));
        menu.setBackgroundColor(Color.rgb(12, 11, 26));
        body.addView(menu, new LinearLayout.LayoutParams(dp(205), -1));
        addMenu(menu, "INÍCIO", "home");
        addMenu(menu, "TV AO VIVO", "live");
        addMenu(menu, "FILMES", "movie");
        addMenu(menu, "SÉRIES", "series");
        addMenu(menu, "FAVORITOS", "favorites");
        addMenu(menu, "CONFIGURAÇÕES", "settings");
        View spacer = new View(this);
        menu.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        TextView version = text("MG Play  •  1.0.0", 10, MUTED, false);
        menu.addView(version, new LinearLayout.LayoutParams(-1, dp(34)));

        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(24), dp(18), dp(24), dp(12));
        body.addView(area, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        area.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(52)));
        String title = sectionTitle();
        TextView heading = text(title, 24, Color.WHITE, true);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        titleRow.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        area.addView(tools, new LinearLayout.LayoutParams(-1, dp(54)));
        Button refresh = button("ATUALIZAR LISTAS", false);
        tools.addView(refresh, new LinearLayout.LayoutParams(dp(175), dp(44)));
        refresh.setOnClickListener(v -> refreshCatalog());
        Button panel = button("PAINEL WEB", false);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(145), dp(44));
        pp.leftMargin = dp(10);
        tools.addView(panel, pp);
        panel.setOnClickListener(v -> openPanel());
        status = text("Conecte-se ao painel para adicionar suas listas.", 11, MUTED, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1);
        sp.leftMargin = dp(14);
        tools.addView(status, sp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(6), 0, dp(28));
        scroll.addView(content);
        area.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        if (section.equals("settings")) renderSettings();
        else renderCatalog();
    }

    private void addMenu(LinearLayout menu, String label, String id) {
        Button item = button(label, section.equals(id));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.bottomMargin = dp(8);
        menu.addView(item, lp);
        item.setOnClickListener(v -> { section = id; showScreen(); });
    }

    private String sectionTitle() {
        switch (section) {
            case "live": return "TV AO VIVO";
            case "movie": return "FILMES";
            case "series": return "SÉRIES";
            case "favorites": return "FAVORITOS";
            case "settings": return "CONFIGURAÇÕES";
            default: return "BEM-VINDO AO MG PLAY";
        }
    }

    private void renderCatalog() {
        content.removeAllViews();
        JSONArray catalog = PlaylistStore.getCatalog(this);
        if (catalog.length() == 0) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(30), dp(55), dp(30), dp(30));
            empty.setBackground(background(PANEL, 18));
            content.addView(empty, new LinearLayout.LayoutParams(-1, dp(330)));
            TextView headline = text("SEU ENTRETENIMENTO COMEÇA AQUI", 23, Color.WHITE, true);
            headline.setGravity(Gravity.CENTER);
            empty.addView(headline);
            TextView help = text("Acesse o Painel MG Play pelo celular ou computador, na mesma rede da TV Box. Cadastre sua lista Xtream Codes ou M3U e depois pressione ATUALIZAR LISTAS.", 14, MUTED, false);
            help.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
            hp.topMargin = dp(16);
            empty.addView(help, hp);
            TextView url = text("http://" + PanelServer.localIp() + ":" + PanelServer.PORT + "/", 16, Color.rgb(186, 113, 255), true);
            url.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1, -2);
            up.topMargin = dp(22);
            empty.addView(url, up);
            TextView code = text("MAC: " + DeviceIdentity.mac(this), 12, Color.WHITE, true);
            code.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(12);
            empty.addView(code, cp);
            return;
        }

        if (section.equals("home")) {
            addSection("CANAIS EM DESTAQUE", filter(catalog, "live", false));
            addSection("FILMES EM DESTAQUE", filter(catalog, "movie", false));
            addSection("SÉRIES", filter(catalog, "series", false));
        } else if (section.equals("favorites")) {
            addSection("SEUS FAVORITOS", filter(catalog, null, true));
        } else {
            String type = section.equals("live") ? "live" : section;
            addSection(sectionTitle(), filter(catalog, type, false));
        }
    }

    private List<JSONObject> filter(JSONArray catalog, String type, boolean onlyFavorites) {
        Set<String> favorites = getFavorites();
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject item = catalog.optJSONObject(i);
            if (item == null) continue;
            if (type != null && !type.equals(item.optString("type"))) continue;
            if (onlyFavorites && !favorites.contains(item.optString("id"))) continue;
            result.add(item);
        }
        return result;
    }

    private void addSection(String title, List<JSONObject> items) {
        if (items.isEmpty()) return;
        TextView label = text(title, 19, Color.WHITE, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
        lp.topMargin = dp(12);
        content.addView(label, lp);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(row);
        content.addView(horizontal, new LinearLayout.LayoutParams(-1, dp(245)));
        int count = Math.min(items.size(), section.equals("home") ? 12 : 500);
        for (int i = 0; i < count; i++) row.addView(card(items.get(i)));
    }

    private View card(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackground(background(Color.rgb(22, 20, 42), 12));
        card.setFocusable(true);
        card.setClickable(true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(158), dp(226));
        cp.setMargins(0, 0, dp(12), 0);
        card.setLayoutParams(cp);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.rgb(36, 31, 61));
        card.addView(poster, new LinearLayout.LayoutParams(-1, dp(166)));
        ImageLoader.load(poster, item.optString("poster"));

        TextView title = text(item.optString("title", "Conteúdo"), 12, Color.WHITE, true);
        title.setMaxLines(2);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, 0, 1);
        tp.topMargin = dp(4);
        card.addView(title, tp);
        card.setOnFocusChangeListener((view, focused) -> view.setBackground(background(focused ? Color.rgb(65, 33, 105) : Color.rgb(22, 20, 42), 12)));
        card.setOnClickListener(v -> openItem(item));
        card.setOnLongClickListener(v -> { toggleFavorite(item); return true; });
        return card;
    }

    private void renderSettings() {
        content.removeAllViews();
        addSetting("MAC DO MG PLAY", DeviceIdentity.mac(this));
        addSetting("CHAVE DO DISPOSITIVO", DeviceIdentity.key(this));
        addSetting("ENDEREÇO DO PAINEL", "http://" + PanelServer.localIp() + ":" + PanelServer.PORT + "/");
        TextView note = text("O identificador MAC do MG Play é criado para este aplicativo. Ele permanece estável neste aparelho e é diferente do endereço MAC físico do Wi-Fi.", 12, MUTED, false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(16);
        content.addView(note, np);
        Button copy = button("COPIAR DADOS DO DISPOSITIVO", false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(300), dp(48));
        bp.topMargin = dp(20);
        content.addView(copy, bp);
        copy.setOnClickListener(v -> {
            String data = "MAC: " + DeviceIdentity.mac(this) + "\nCHAVE: " + DeviceIdentity.key(this) + "\nPAINEL: http://" + PanelServer.localIp() + ":" + PanelServer.PORT + "/";
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("MG Play", data));
            Toast.makeText(this, "Dados copiados.", Toast.LENGTH_SHORT).show();
        });
        Button open = button("ABRIR PAINEL NESTE APARELHO", false);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(300), dp(48));
        op.topMargin = dp(10);
        content.addView(open, op);
        open.setOnClickListener(v -> openPanel());
    }

    private void addSetting(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(17), dp(13), dp(17), dp(13));
        box.setBackground(background(PANEL, 12));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.bottomMargin = dp(10);
        content.addView(box, bp);
        box.addView(text(label, 11, MUTED, true));
        TextView val = text(value, 15, Color.WHITE, true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.topMargin = dp(7);
        box.addView(val, vp);
    }

    private void openPanel() {
        String url = "http://" + PanelServer.localIp() + ":" + PanelServer.PORT + "/";
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "Painel: " + url, Toast.LENGTH_LONG).show(); }
    }

    private void refreshCatalog() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (status != null) status.setText("Atualizando catálogo...");
        io.execute(() -> {
            try {
                JSONArray catalog = CatalogLoader.load(this);
                PlaylistStore.saveCatalog(this, catalog);
                runOnUiThread(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    if (status != null) status.setText(catalog.length() + " itens carregados");
                    renderCatalog();
                    Toast.makeText(this, "Catálogo atualizado: " + catalog.length() + " itens.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    if (status != null) status.setText("Falha ao atualizar");
                    Toast.makeText(this, e.getMessage() == null ? "Falha ao atualizar listas." : e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openItem(JSONObject item) {
        String url = item.optString("stream_url");
        if (!url.isEmpty()) { play(url, item.optString("title")); return; }
        if (!"series".equals(item.optString("type"))) {
            Toast.makeText(this, "Este item ainda não possui um link de reprodução.", Toast.LENGTH_LONG).show();
            return;
        }
        io.execute(() -> {
            try {
                JSONArray episodes = CatalogLoader.seriesEpisodes(this, item);
                runOnUiThread(() -> showEpisodes(item.optString("title"), episodes));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showEpisodes(String title, JSONArray episodes) {
        if (episodes.length() == 0) { Toast.makeText(this, "Nenhum episódio encontrado.", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[episodes.length()];
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject episode = episodes.optJSONObject(i);
            labels[i] = "T" + (episode == null ? "?" : episode.optString("season")) + " • E" + (episode == null ? "?" : episode.optString("episode")) + "  " + (episode == null ? "Episódio" : episode.optString("title"));
        }
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (dialog, which) -> {
            JSONObject episode = episodes.optJSONObject(which);
            if (episode != null) play(episode.optString("stream_url"), title + " - " + episode.optString("title"));
        }).setNegativeButton("FECHAR", null).show();
    }

    private void play(String url, String title) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("title", title);
        startActivity(intent);
    }

    private SharedPreferences favoritesPrefs() { return getSharedPreferences("mgplay_favorites", MODE_PRIVATE); }
    private Set<String> getFavorites() { return new HashSet<>(favoritesPrefs().getStringSet("ids", new HashSet<>())); }
    private void toggleFavorite(JSONObject item) {
        Set<String> ids = getFavorites();
        String id = item.optString("id");
        if (!ids.add(id)) ids.remove(id);
        favoritesPrefs().edit().putStringSet("ids", ids).apply();
        Toast.makeText(this, ids.contains(id) ? "Adicionado aos favoritos." : "Removido dos favoritos.", Toast.LENGTH_SHORT).show();
        if (section.equals("favorites")) renderCatalog();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null && !section.equals("settings")) renderCatalog();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}