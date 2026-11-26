package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ContentItem base;
    private String type;
    private LinearLayout page;
    private LinearLayout info;
    private TextView status;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        return view;
    }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String id = getIntent().getStringExtra("id");
        type = getIntent().getStringExtra("type");
        String title = getIntent().getStringExtra("title");
        if (id == null || type == null || !("movie".equals(type) || "series".equals(type))) {
            finish();
            return;
        }
        synchronized (CatalogStore.ITEMS) {
            for (ContentItem item : CatalogStore.ITEMS) {
                if (item.id.equals(id) && item.type.equals(type)) {
                    base = item;
                    break;
                }
            }
        }
        if (base == null) {
            base = new ContentItem();
            base.id = id;
            base.type = type;
            base.title = title == null ? "" : title;
        }
        showLoading();
        io.execute(() -> loadDetails(id));
    }

    private void showLoading() {
        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setBackgroundColor(Color.rgb(11, 14, 20));
        ProgressBar progress = new ProgressBar(this);
        loading.addView(progress, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView label = text("Carregando informações...", 18);
        label.setGravity(Gravity.CENTER);
        loading.addView(label, new LinearLayout.LayoutParams(-1, dp(58)));
        setContentView(loading);
    }

    private void loadDetails(String id) {
        try {
            JSONObject response;
            if ("movie".equals(type)) {
                response = MarcinApi.catalog(SessionStore.username(), SessionStore.password(),
                        "get_vod_info", "vod_id", id);
                applyMovieInfo(response.optJSONObject("data"));
                runOnUiThread(this::showMovie);
            } else {
                response = MarcinApi.catalog(SessionStore.username(), SessionStore.password(),
                        "get_series_info", "series_id", id);
                List<ContentItem> episodes = parseEpisodes(response.optJSONObject("data"));
                applySeriesInfo(response.optJSONObject("data"));
                runOnUiThread(() -> showSeries(episodes));
            }
        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    private void applyMovieInfo(JSONObject data) {
        if (data == null) return;
        JSONObject info = data.optJSONObject("info");
        JSONObject movie = data.optJSONObject("movie_data");
        if (info != null) {
            String poster = info.optString("movie_image", "");
            if (!poster.isEmpty()) base.posterUrl = poster;
            base.description = info.optString("plot", base.description);
            base.genres = info.optString("genre", base.genres);
            base.year = info.optString("releasedate", base.year);
        }
        if (movie != null) {
            base.containerExtension = movie.optString("container_extension", base.containerExtension);
            if (base.id.isEmpty()) base.id = movie.optString("stream_id", "");
        }
    }

    private void applySeriesInfo(JSONObject data) {
        if (data == null) return;
        JSONObject info = data.optJSONObject("info");
        if (info == null) return;
        String cover = info.optString("cover", "");
        if (!cover.isEmpty()) base.posterUrl = cover;
        base.description = info.optString("plot", base.description);
        base.genres = info.optString("genre", base.genres);
        base.year = info.optString("releaseDate", base.year);
    }

    private List<ContentItem> parseEpisodes(JSONObject data) {
        List<ContentItem> result = new ArrayList<>();
        if (data == null) return result;
        JSONObject seasons = data.optJSONObject("episodes");
        if (seasons == null) return result;
        Iterator<String> keys = seasons.keys();
        while (keys.hasNext()) {
            String seasonKey = keys.next();
            JSONArray list = seasons.optJSONArray(seasonKey);
            if (list == null) continue;
            int seasonNumber = parseInt(seasonKey);
            for (int i = 0; i < list.length(); i++) {
                JSONObject row = list.optJSONObject(i);
                if (row == null) continue;
                ContentItem episode = new ContentItem();
                episode.type = "series";
                episode.id = row.optString("id", "");
                if (!episode.id.matches("[0-9]+")) continue;
                episode.title = base.title;
                episode.episodeTitle = row.optString("title", "");
                episode.season = row.optInt("season", seasonNumber);
                episode.episode = row.optInt("episode_num", row.optInt("episode", i + 1));
                episode.containerExtension = row.optString("container_extension", "");
                JSONObject episodeInfo = row.optJSONObject("info");
                if (episodeInfo != null) {
                    episode.description = episodeInfo.optString("plot", "");
                    episode.posterUrl = episodeInfo.optString("movie_image", "");
                }
                result.add(episode);
            }
        }
        Collections.sort(result, Comparator
                .comparingInt((ContentItem x) -> x.season)
                .thenComparingInt(x -> x.episode));
        return result;
    }

    private void showMovie() {
        buildPage(false);
        TextView name = text(base.displayTitle(), 30);
        name.setTypeface(null, 1);
        info.addView(name, new LinearLayout.LayoutParams(-1, dp(66)));
        TextView genres = text(base.genres, 17);
        genres.setTextColor(Color.rgb(170, 178, 192));
        info.addView(genres, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView description = text(base.description, 18);
        description.setTextColor(Color.rgb(220, 224, 232));
        description.setLineSpacing(0, 1.15f);
        info.addView(description, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button play = button("▶  REPRODUZIR");
        play.setOnClickListener(v -> {
            if (base.containerExtension == null || base.containerExtension.trim().isEmpty()) {
                Toast.makeText(this, "O fornecedor não informou o formato deste filme.", Toast.LENGTH_LONG).show();
                return;
            }
            play(base.id, "movie", base.containerExtension, base.title);
        });
        info.addView(play, new LinearLayout.LayoutParams(dp(300), dp(62)));
        setContentView(page);
    }

    private void showSeries(List<ContentItem> episodes) {
        buildPage(true);
        TextView name = text(base.title, 30);
        name.setTypeface(null, 1);
        info.addView(name, new LinearLayout.LayoutParams(-1, dp(62)));
        TextView genres = text(base.genres, 17);
        genres.setTextColor(Color.rgb(170, 178, 192));
        info.addView(genres, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView description = text(base.description, 17);
        description.setTextColor(Color.rgb(220, 224, 232));
        description.setLineSpacing(0, 1.12f);
        info.addView(description, new LinearLayout.LayoutParams(-1, dp(116)));

        TextView episodeHeading = text("Temporadas e episódios", 21);
        episodeHeading.setTypeface(null, 1);
        info.addView(episodeHeading, new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        info.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        int currentSeason = -1;
        for (ContentItem episode : episodes) {
            if (episode.season != currentSeason) {
                currentSeason = episode.season;
                TextView season = text("TEMPORADA " + currentSeason, 17);
                season.setTypeface(null, 1);
                season.setTextColor(Color.rgb(170, 178, 192));
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(42));
                sp.setMargins(0, dp(8), 0, 0);
                list.addView(season, sp);
            }
            String label = "T" + episode.season + "  E" + episode.episode;
            if (!episode.episodeTitle.isEmpty()) label += "  •  " + episode.episodeTitle;
            final String episodeLabel = label;
            Button button = button(episodeLabel);
            button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            button.setOnClickListener(v -> {
                if (episode.containerExtension == null || episode.containerExtension.trim().isEmpty()) {
                    Toast.makeText(this, "O fornecedor não informou o formato deste episódio.", Toast.LENGTH_LONG).show();
                    return;
                }
                play(episode.id, "series", episode.containerExtension, episodeLabel);
            });
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(54));
            ep.setMargins(0, 0, 0, dp(7));
            list.addView(button, ep);
        }
        if (episodes.isEmpty()) {
            TextView empty = text("Nenhum episódio disponível.", 17);
            empty.setTextColor(Color.rgb(170, 178, 192));
            list.addView(empty);
        }
        setContentView(page);
    }

    private void buildPage(boolean isSeries) {
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(dp(48), dp(36), dp(48), dp(36));
        page.setBackgroundColor(Color.rgb(11, 14, 20));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.rgb(32, 38, 48));
        page.addView(poster, new LinearLayout.LayoutParams(dp(300), dp(440)));
        ImageLoader.load(poster, base.posterUrl);

        info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(38), 0, 0, 0);
        page.addView(info, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setFocusable(true);
        b.setBackground(bg(Color.rgb(124, 77, 255), 10));
        b.setPadding(dp(18), 0, dp(18), 0);
        return b;
    }

    private void play(String id, String kind, String extension, String title) {
        Intent intent = new Intent(this, PlaybackActivity.class);
        intent.putExtra("id", id);
        intent.putExtra("kind", kind);
        intent.putExtra("extension", extension);
        intent.putExtra("title", title);
        startActivity(intent);
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Não foi possível carregar este conteúdo." : message;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) io.shutdown();
    }
}
