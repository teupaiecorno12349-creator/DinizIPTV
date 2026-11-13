package com.mgplay.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public final class CatalogLoader {
    private static final int MAX_ITEMS = 6000;
    private CatalogLoader() {}

    public static JSONArray load(Context context) throws Exception {
        JSONArray result = new JSONArray();
        JSONArray providers = PlaylistStore.get(context);
        if (providers.length() == 0) throw new IllegalStateException("Adicione uma lista pelo Painel MG Play antes de atualizar.");
        StringBuilder errors = new StringBuilder();
        for (int i = 0; i < providers.length(); i++) {
            JSONObject provider = providers.optJSONObject(i);
            if (provider == null) continue;
            try {
                if ("xtream".equalsIgnoreCase(provider.optString("type"))) appendXtream(provider, result);
                else appendM3u(provider, result);
            } catch (Exception e) {
                if (errors.length() > 0) errors.append("\n");
                errors.append(provider.optString("name", "Lista")).append(": ")
                    .append(e.getMessage() == null ? "falha ao carregar" : e.getMessage());
            }
            if (result.length() >= MAX_ITEMS) break;
        }
        if (result.length() == 0) throw new IllegalStateException(errors.length() == 0 ? "Nenhum conteúdo foi encontrado." : errors.toString());
        return result;
    }

    private static void appendXtream(JSONObject provider, JSONArray out) throws Exception {
        String base = normalizeBase(provider.optString("server_url"));
        String username = provider.optString("username");
        String password = provider.optString("password");
        String query = "username=" + enc(username) + "&password=" + enc(password);
        JSONObject auth = getObject(base + "/player_api.php?" + query);
        JSONObject userInfo = auth.optJSONObject("user_info");
        if (userInfo != null && userInfo.has("auth") && userInfo.optInt("auth", 1) == 0) {
            throw new IllegalStateException("Usuário ou senha rejeitados pelo servidor.");
        }

        Map<String, String> liveCategories = categories(base, query, "get_live_categories");
        Map<String, String> movieCategories = categories(base, query, "get_vod_categories");
        Map<String, String> seriesCategories = categories(base, query, "get_series_categories");
        appendStreams(base, query, provider, out, "get_live_streams", "live", liveCategories);
        appendStreams(base, query, provider, out, "get_vod_streams", "movie", movieCategories);
        appendStreams(base, query, provider, out, "get_series", "series", seriesCategories);
    }

    private static Map<String, String> categories(String base, String query, String action) {
        Map<String, String> result = new HashMap<>();
        try {
            JSONArray list = getArray(base + "/player_api.php?" + query + "&action=" + action);
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null) result.put(item.optString("category_id"), item.optString("category_name", "Sem categoria"));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static void appendStreams(String base, String query, JSONObject provider, JSONArray out,
                                      String action, String type, Map<String, String> categories) throws Exception {
        JSONArray list = getArray(base + "/player_api.php?" + query + "&action=" + action);
        for (int i = 0; i < list.length() && out.length() < MAX_ITEMS; i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("stream_id", item.optString("series_id", item.optString("id")));
            String name = item.optString("name", item.optString("title", "Conteúdo"));
            String categoryId = item.optString("category_id");
            String category = categories.containsKey(categoryId) ? categories.get(categoryId) : "Sem categoria";
            String image = item.optString("stream_icon", item.optString("cover", ""));
            String extension = item.optString("container_extension", "");
            String streamUrl = "";
            if (type.equals("live")) streamUrl = base + "/live/" + Uri.encode(provider.optString("username")) + "/" + Uri.encode(provider.optString("password")) + "/" + id + ".ts";
            if (type.equals("movie")) {
                if (extension.isEmpty()) extension = "mp4";
                streamUrl = base + "/movie/" + Uri.encode(provider.optString("username")) + "/" + Uri.encode(provider.optString("password")) + "/" + id + "." + extension;
            }
            JSONObject entry = new JSONObject().put("id", provider.optString("id") + ":" + type + ":" + id)
                .put("provider_id", provider.optString("id")).put("provider_name", provider.optString("name"))
                .put("type", type).put("title", name).put("category", category)
                .put("poster", image).put("stream_url", streamUrl).put("remote_id", id);
            out.put(entry);
        }
    }

    private static void appendM3u(JSONObject provider, JSONArray out) throws Exception {
        String playlist = getText(provider.optString("m3u_url"), 30 * 1024 * 1024);
        BufferedReader reader = new BufferedReader(new java.io.StringReader(playlist));
        String line;
        String metadata = null;
        int index = 0;
        while ((line = reader.readLine()) != null && out.length() < MAX_ITEMS) {
            line = line.trim();
            if (line.startsWith("#EXTINF:")) { metadata = line; continue; }
            if (line.isEmpty() || line.startsWith("#") || metadata == null) continue;
            String title = metadata.substring(metadata.lastIndexOf(',') + 1).trim();
            if (title.isEmpty()) title = "Canal " + (index + 1);
            String group = attribute(metadata, "group-title");
            String poster = attribute(metadata, "tvg-logo");
            String lower = (group + " " + line).toLowerCase(Locale.ROOT);
            String type = (lower.contains("filme") || lower.contains("movie")) ? "movie"
                : (lower.contains("série") || lower.contains("serie") || lower.contains("series")) ? "series" : "live";
            JSONObject item = new JSONObject().put("id", provider.optString("id") + ":m3u:" + index)
                .put("provider_id", provider.optString("id")).put("provider_name", provider.optString("name"))
                .put("type", type).put("title", title).put("category", group.isEmpty() ? "Sem categoria" : group)
                .put("poster", poster).put("stream_url", line).put("remote_id", "");
            out.put(item);
            index++;
            metadata = null;
        }
    }

    public static JSONArray seriesEpisodes(Context context, JSONObject series) throws Exception {
        JSONArray providers = PlaylistStore.get(context);
        JSONObject provider = null;
        String providerId = series.optString("provider_id");
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.optJSONObject(i);
            if (p != null && providerId.equals(p.optString("id"))) { provider = p; break; }
        }
        if (provider == null) throw new IllegalStateException("A lista desta série não está mais cadastrada.");
        String base = normalizeBase(provider.optString("server_url"));
        String query = "username=" + enc(provider.optString("username")) + "&password=" + enc(provider.optString("password"));
        JSONObject info = getObject(base + "/player_api.php?" + query + "&action=get_series_info&series_id=" + enc(series.optString("remote_id")));
        JSONObject seasons = info.optJSONObject("episodes");
        JSONArray result = new JSONArray();
        if (seasons == null) return result;
        Iterator<String> keys = seasons.keys();
        while (keys.hasNext()) {
            String season = keys.next();
            JSONArray episodes = seasons.optJSONArray(season);
            if (episodes == null) continue;
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject episode = episodes.optJSONObject(i);
                if (episode == null) continue;
                String id = episode.optString("id");
                String ext = episode.optString("container_extension", "mp4");
                if (ext.isEmpty()) ext = "mp4";
                String url = base + "/series/" + Uri.encode(provider.optString("username")) + "/" + Uri.encode(provider.optString("password")) + "/" + id + "." + ext;
                result.put(new JSONObject().put("title", episode.optString("title", "Episódio " + (i + 1)))
                    .put("season", season).put("episode", episode.optString("episode_num", String.valueOf(i + 1)))
                    .put("stream_url", url));
            }
        }
        return result;
    }

    private static JSONObject getObject(String url) throws Exception {
        String body = getText(url, 12 * 1024 * 1024);
        return new JSONObject(body);
    }

    private static JSONArray getArray(String url) throws Exception {
        String body = getText(url, 24 * 1024 * 1024);
        return new JSONArray(body);
    }

    private static String getText(String address, int maxBytes) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "MGPlay/1.0 AndroidTV");
            connection.setRequestProperty("Accept", "application/json, application/x-mpegURL, */*");
            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) throw new IllegalStateException("Servidor respondeu HTTP " + code);
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maxBytes) throw new IllegalStateException("Resposta muito grande para processar.");
                output.write(buffer, 0, count);
            }
            input.close();
            String body = output.toString("UTF-8");
            if (code < 200 || code >= 300) throw new IllegalStateException("Servidor respondeu HTTP " + code);
            return body;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private static String normalizeBase(String value) {
        String base = value == null ? "" : value.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/player_api.php")) base = base.substring(0, base.length() - 15);
        if (base.endsWith("/get.php")) base = base.substring(0, base.length() - 8);
        return base;
    }

    private static String enc(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }

    private static String attribute(String source, String name) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|\\s)" + name + "=\"([^\"]*)\"", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(source);
        return matcher.find() ? matcher.group(1) : "";
    }
}