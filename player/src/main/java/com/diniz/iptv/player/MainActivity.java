package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private EditText search;
    private String filter = "all";
    private boolean loading;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (SessionStore.isActive()) {
            reloadCatalog();
        } else {
            showLogin();
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        return t;
    }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void showLogin() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(48), dp(40), dp(48), dp(30));
        root.setBackgroundColor(Color.rgb(11, 14, 20));
        scroll.addView(root);

        TextView logo = text("DINIZ IPTV", 30);
        logo.setTypeface(null, 1);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(68)));
        TextView subtitle = text("Entre com seu acesso de cliente", 17);
        subtitle.setTextColor(Color.rgb(170, 178, 192));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(48)));

        EditText user = field("Usuário do cliente");
        EditText pass = field("Senha do cliente");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Button login = button("ENTRAR");
        ProgressBar progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView status = text("", 15);
        status.setTextColor(Color.rgb(255, 180, 180));
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(58)));

        login.setOnClickListener(v -> {
            String username = user.getText().toString().trim();
            String password = pass.getText().toString();
            if (username.isEmpty() || password.isEmpty()) {
                status.setText("Informe seu usuário e sua senha.");
                return;
            }
            login.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            status.setText("Validando acesso...");
            io.execute(() -> {
                try {
                    JSONObject session = MarcinApi.login(username, password);
                    String providerBase = session.getJSONObject("provider").getString("base_url");
                    SessionStore.set(username, password, providerBase);
                    loadCatalogFromPanel();
                    runOnUiThread(this::showHome);
                } catch (Exception e) {
                    SessionStore.clear();
                    runOnUiThread(() -> {
                        login.setEnabled(true);
                        progress.setVisibility(View.GONE);
                        status.setText(safeMessage(e));
                    });
                }
            });
        });
        setContentView(scroll);
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(150, 158, 172));
        e.setTextColor(Color.WHITE);
        e.setSingleLine(true);
        e.setPadding(dp(18), 0, dp(18), 0);
        e.setBackground(bg(Color.rgb(21, 26, 35), 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Math.min(dp(650), getResources().getDisplayMetrics().widthPixels - dp(140)), dp(62));
        lp.setMargins(0, dp(9), 0, dp(9));
        root.addView(e, lp);
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setBackground(bg(Color.rgb(124, 77, 255), 10));
        b.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(300), dp(60));
        lp.setMargins(0, dp(14), 0, dp(8));
        root.addView(b, lp);
        return b;
    }

    private void reloadCatalog() {
        if (!SessionStore.isActive() || loading) return;
        loading = true;
        io.execute(() -> {
            try {
                loadCatalogFromPanel();
                runOnUiThread(this::showHome);
            } catch (Exception e) {
                SessionStore.clear();
                runOnUiThread(() -> {
                    loading = false;
                    showLogin();
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadCatalogFromPanel() throws Exception {
        String user = SessionStore.username();
        String pass = SessionStore.password();

        Map<String, String> liveNames = categoryNames(user, pass, "get_live_categories");
        Map<String, String> movieNames = categoryNames(user, pass, "get_vod_categories");
        Map<String, String> seriesNames = categoryNames(user, pass, "get_series_categories");

        List<ContentItem> items = new ArrayList<>();
        addItems(items, data(MarcinApi.catalog(user, pass, "get_live_streams")), "live", liveNames);
        addItems(items, data(MarcinApi.catalog(user, pass, "get_vod_streams")), "movie", movieNames);
        addItems(items, data(MarcinApi.catalog(user, pass, "get_series")), "series", seriesNames);

        synchronized (CatalogStore.ITEMS) {
            CatalogStore.ITEMS.clear();
            CatalogStore.ITEMS.addAll(items);
        }
        loading = false;
    }

    private Map<String, String> categoryNames(String user, String pass, String action) {
        Map<String, String> names = new LinkedHashMap<>();
        try {
            JSONArray list = data(MarcinApi.catalog(user, pass, action));
            for (int i = 0; i < list.length(); i++) {
                JSONObject row = list.optJSONObject(i);
                if (row == null) continue;
                String id = row.optString("category_id", "");
                String name = row.optString("category_name", "Outros");
                if (!id.isEmpty()) names.put(id, name);
            }
        } catch (Exception ignored) {
            // A ausência de categorias não impede a exibição das listas de conteúdo.
        }
        return names;
    }

    private JSONArray data(JSONObject response) {
        JSONArray array = response.optJSONArray("data");
        return array == null ? new JSONArray() : array;
    }

    private void addItems(List<ContentItem> target, JSONArray source, String type,
                          Map<String, String> categoryNames) {
        for (int i = 0; i < source.length(); i++) {
            JSONObject row = source.optJSONObject(i);
            if (row == null) continue;
            ContentItem item = new ContentItem();
            item.type = type;
            item.id = "live".equals(type) || "movie".equals(type)
                    ? row.optString("stream_id", "") : row.optString("series_id", "");
            if (item.id.isEmpty() || !item.id.matches("[0-9]+")) continue;
            item.title = row.optString("name", "Sem título");
            item.categoryId = row.optString("category_id", "");
            item.category = categoryNames.getOrDefault(item.categoryId, "Outros");
            item.posterUrl = "series".equals(type)
                    ? row.optString("cover", row.optString("stream_icon", ""))
                    : row.optString("stream_icon", "");
            item.year = row.optString("year", row.optString("releaseDate", ""));
            item.description = row.optString("plot", "");
            item.genres = row.optString("genre", "");
            item.containerExtension = row.optString("container_extension", "");
            if ("live".equals(type)) item.containerExtension = "ts";
            target.add(item);
        }
    }

    private void showHome() {
        loading = false;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(11, 14, 20));
        page.setPadding(dp(30), dp(16), dp(30), dp(18));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(68)));
        TextView brand = text("DINIZ IPTV", 24);
        brand.setTypeface(null, 1);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        search = new EditText(this);
        search.setHint("Pesquisar canais, filmes e séries");
        search.setHintTextColor(Color.rgb(150, 158, 172));
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(12));
        search.setBackground(bg(Color.rgb(21, 26, 35), 12));
        search.setPadding(dp(16), 0, dp(16), 0);
        top.addView(search, new LinearLayout.LayoutParams(dp(430), dp(52)));

        Button refresh = smallButton("↻");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(58), dp(52));
        refreshParams.setMargins(dp(10), 0, 0, 0);
        top.addView(refresh, refreshParams);
        refresh.setOnClickListener(v -> {
            if (loading) return;
            loading = true;
            Toast.makeText(this, "Atualizando catálogo...", Toast.LENGTH_SHORT).show();
            reloadCatalog();
        });

        Button logout = smallButton("SAIR");
        LinearLayout.LayoutParams logoutParams = new LinearLayout.LayoutParams(dp(92), dp(52));
        logoutParams.setMargins(dp(10), 0, 0, 0);
        top.addView(logout, logoutParams);
        logout.setOnClickListener(v -> {
            SessionStore.clear();
            filter = "all";
            showLogin();
        });

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(tabs, new LinearLayout.LayoutParams(-1, dp(62)));
        addTab(tabs, "TODOS", "all");
        addTab(tabs, "TV AO VIVO", "live");
        addTab(tabs, "FILMES", "movie");
        addTab(tabs, "SÉRIES", "series");

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(8), 0, dp(36));
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Runnable render = () -> renderRows(rows, search.getText().toString());
        render.run();
        search.setOnEditorActionListener((v, action, event) -> { render.run(); return true; });
        search.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == 1) render.run();
            return false;
        });
        setContentView(page);
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setFocusable(true);
        b.setBackground(bg(Color.rgb(21, 26, 35), 10));
        return b;
    }

    private void addTab(LinearLayout tabs, String label, String value) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setFocusable(true);
        b.setBackground(bg(value.equals(filter) ? Color.rgb(124, 77, 255) : Color.rgb(21, 26, 35), 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(46));
        lp.setMargins(0, 0, dp(10), 0);
        tabs.addView(b, lp);
        b.setOnClickListener(v -> {
            filter = value;
            for (int i = 0; i < tabs.getChildCount(); i++) {
                View child = tabs.getChildAt(i);
                if (child instanceof Button) {
                    String childText = ((Button) child).getText().toString();
                    String selected = "TODOS".equals(childText) ? "all"
                            : "TV AO VIVO".equals(childText) ? "live"
                            : "FILMES".equals(childText) ? "movie" : "series";
                    child.setBackground(bg(selected.equals(filter)
                            ? Color.rgb(124, 77, 255) : Color.rgb(21, 26, 35), 9));
                }
            }
            View parent = (View) tabs.getParent();
            if (parent != null) {
                View scroll = (View) parent.getParent();
                if (scroll instanceof ScrollView) {
                    // The rows are refreshed by the active filter below.
                }
            }
            if (search != null) search.clearFocus();
            if (tabs.getParent() instanceof LinearLayout) {
                LinearLayout page = (LinearLayout) tabs.getParent();
                if (page.getChildCount() > 2 && page.getChildAt(2) instanceof ScrollView) {
                    ScrollView container = (ScrollView) page.getChildAt(2);
                    if (container.getChildAt(0) instanceof LinearLayout) {
                        renderRows((LinearLayout) container.getChildAt(0), search == null ? "" : search.getText().toString());
                    }
                }
            }
        });
    }

    private void renderRows(LinearLayout rows, String query) {
        rows.removeAllViews();
        List<ContentItem> snapshot;
        synchronized (CatalogStore.ITEMS) { snapshot = new ArrayList<>(CatalogStore.ITEMS); }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        if ("all".equals(filter) || "live".equals(filter)) {
            addGroupedRows(rows, snapshot, "live", q, "TV AO VIVO");
        }
        if ("all".equals(filter) || "movie".equals(filter)) {
            addGroupedRows(rows, snapshot, "movie", q, "FILMES");
        }
        if ("all".equals(filter) || "series".equals(filter)) {
            addGroupedRows(rows, snapshot, "series", q, "SÉRIES");
        }
        if (rows.getChildCount() == 0) {
            TextView empty = text("Nenhum conteúdo encontrado.", 19);
            empty.setTextColor(Color.rgb(170, 178, 192));
            empty.setGravity(Gravity.CENTER);
            rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(130)));
        }
    }

    private void addGroupedRows(LinearLayout rows, List<ContentItem> source, String type,
                                String query, String section) {
        Map<String, List<ContentItem>> groups = new LinkedHashMap<>();
        for (ContentItem item : source) {
            if (!type.equals(item.type)) continue;
            String haystack = (item.title + " " + item.genres + " " + item.category)
                    .toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            String group = item.category == null || item.category.trim().isEmpty()
                    ? "Outros" : item.category;
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(item);
        }
        if (groups.isEmpty()) return;
        for (Map.Entry<String, List<ContentItem>> entry : groups.entrySet()) {
            String heading = ("all".equals(filter) ? section + "  •  " : "") + entry.getKey();
            addRow(rows, heading, entry.getValue());
        }
    }

    private void addRow(LinearLayout rows, String title, List<ContentItem> items) {
        TextView heading = text(title, 21);
        heading.setTypeface(null, 1);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(48));
        hp.setMargins(0, dp(10), 0, 0);
        rows.addView(heading, hp);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(line);
        rows.addView(hsv, new LinearLayout.LayoutParams(-1, dp(326)));
        for (ContentItem item : items) line.addView(card(item));
    }

    private View card(ContentItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackground(bg(Color.rgb(21, 26, 35), 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(188), dp(306));
        cp.setMargins(0, 0, dp(14), 0);
        card.setLayoutParams(cp);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.rgb(32, 38, 48));
        card.addView(poster, new LinearLayout.LayoutParams(-1, dp(244)));
        ImageLoader.load(poster, item.posterUrl);

        TextView title = text(item.title, 15);
        title.setMaxLines(2);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(4), dp(4), dp(4), 0);
        card.addView(title, new LinearLayout.LayoutParams(-1, 0, 1));

        card.setOnFocusChangeListener((v, focused) ->
                v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                        .setDuration(120).start());
        card.setOnClickListener(v -> {
            if ("live".equals(item.type)) {
                openPlayback(item, "live", "ts", item.title);
            } else {
                Intent intent = new Intent(this, DetailActivity.class);
                intent.putExtra("id", item.id);
                intent.putExtra("type", item.type);
                intent.putExtra("title", item.title);
                startActivity(intent);
            }
        });
        return card;
    }

    private void openPlayback(ContentItem item, String kind, String extension, String title) {
        Intent intent = new Intent(this, PlaybackActivity.class);
        intent.putExtra("kind", kind);
        intent.putExtra("id", item.id);
        intent.putExtra("extension", extension);
        intent.putExtra("title", title);
        startActivity(intent);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return "Não foi possível concluir a operação.";
        return message;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) io.shutdown();
    }
}
