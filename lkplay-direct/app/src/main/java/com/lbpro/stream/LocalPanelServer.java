package com.lbpro.stream;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import fi.iki.elonen.NanoHTTPD;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalPanelServer extends NanoHTTPD {
    private static final String PREFS = "PREF";
    private static final String MODEL = "APP_INFO_MODEL";
    private static final String KEY_PREF = "lkp_web_key";
    private final Context context;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    public LocalPanelServer(Context context) {
        super("0.0.0.0", 8765);
        this.context = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String deviceId(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = p.getString("mac_address", "");
        if (saved != null && !saved.trim().isEmpty()) return saved.trim();
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null || id.isEmpty() ? "lkplay-device" : id;
    }

    public static String deviceKey(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = p.getString(KEY_PREF, "");
        if (key != null && key.length() >= 24) return key;
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        StringBuilder out = new StringBuilder();
        for (byte b : random) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        key = out.toString();
        p.edit().putString(KEY_PREF, key).apply();
        return key;
    }

    public static String localIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "127.0.0.1";
    }

    private JSONObject appModel() throws Exception {
        String raw = prefs().getString(MODEL, "{}");
        try { return new JSONObject(raw); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private JSONArray storedLists(JSONObject model) {
        JSONArray lists = model.optJSONArray("result");
        return lists == null ? new JSONArray() : lists;
    }

    private void saveLists(JSONObject model, JSONArray lists) throws Exception {
        model.put("result", lists);
        model.put("mac_address", deviceId(context));
        if (!model.has("success")) model.put("success", true);
        prefs().edit().putString(MODEL, model.toString())
                .putString("mac_address", deviceId(context))
                .putString("device_type", "tv").apply();
    }

    private JSONObject body(IHTTPSession session) throws Exception {
        Map<String, String> files = new java.util.HashMap<>();
        session.parseBody(files);
        String raw = files.get("postData");
        return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private String bearer(IHTTPSession session) {
        String header = session.getHeaders().get("authorization");
        return header != null && header.toLowerCase(java.util.Locale.ROOT).startsWith("bearer ")
                ? header.substring(7).trim() : "";
    }

    private boolean valid(String token) {
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    private Response json(Response.IStatus status, JSONObject object) {
        Response response = newFixedLengthResponse(status, "application/json; charset=utf-8", object.toString());
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("X-Content-Type-Options", "nosniff");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT, DELETE, OPTIONS");
        return response;
    }

    private JSONObject error(String message) throws Exception {
        return new JSONObject().put("ok", false).put("error", message);
    }

    @Override public Response serve(IHTTPSession session) {
        try {
            String path = session.getUri();
            Method method = session.getMethod();

            if (method == Method.OPTIONS) return json(Response.Status.OK, new JSONObject().put("ok", true));

            if (method == Method.GET && (path.equals("/") || path.equals("/index.html"))) {
                InputStream input = context.getAssets().open("panel.html");
                byte[] bytes = read(input);
                Response response = newFixedLengthResponse(Response.Status.OK,
                        "text/html; charset=utf-8", new java.io.ByteArrayInputStream(bytes), bytes.length);
                response.addHeader("Cache-Control", "no-store");
                return response;
            }

            if (method == Method.GET && path.equals("/health")) {
                return json(Response.Status.OK, new JSONObject().put("ok", true)
                        .put("app", "LK Play").put("device", deviceId(context)));
            }

            if (method == Method.POST && path.equals("/api/panel/login")) {
                JSONObject input = body(session);
                String suppliedId = input.optString("mac", input.optString("deviceId", "")).trim();
                String suppliedKey = input.optString("deviceKey", input.optString("key", ""));
                String expectedId = deviceId(context);
                String expectedKey = deviceKey(context);
                boolean sameId = MessageDigest.isEqual(expectedId.getBytes(StandardCharsets.UTF_8),
                        suppliedId.getBytes(StandardCharsets.UTF_8));
                boolean sameKey = MessageDigest.isEqual(expectedKey.getBytes(StandardCharsets.UTF_8),
                        suppliedKey.getBytes(StandardCharsets.UTF_8));
                if (!sameId || !sameKey) return json(Response.Status.UNAUTHORIZED, error("ID do aparelho ou chave incorretos."));
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                String token = android.util.Base64.encodeToString(random,
                        android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
                sessions.put(token, System.currentTimeMillis() + 43200000L);
                JSONObject device = new JSONObject().put("mac", expectedId).put("name", "LK Play");
                return json(Response.Status.OK, new JSONObject().put("ok", true)
                        .put("access_token", token).put("token_type", "Bearer")
                        .put("expires_in", 43200).put("device", device));
            }

            String token = bearer(session);
            if (!valid(token)) return json(Response.Status.UNAUTHORIZED, error("Sessão expirada. Entre novamente."));

            if (method == Method.GET && path.equals("/api/panel/playlists")) {
                JSONObject model = appModel();
                JSONArray source = storedLists(model);
                JSONArray output = new JSONArray();
                for (int i = 0; i < source.length(); i++) {
                    JSONObject item = source.optJSONObject(i);
                    if (item != null) output.put(toPanel(item));
                }
                return json(Response.Status.OK, new JSONObject().put("ok", true).put("playlists", output));
            }

            if (method == Method.POST && path.equals("/api/panel/playlists")) {
                JSONObject model = appModel();
                JSONArray lists = storedLists(model);
                JSONObject item = fromPanel(body(session), null);
                item.put("id", UUID.randomUUID().toString());
                lists.put(item);
                saveLists(model, lists);
                return json(Response.Status.CREATED, new JSONObject().put("ok", true).put("playlist", toPanel(item)));
            }

            if (path.startsWith("/api/panel/playlists/")) {
                String id = path.substring("/api/panel/playlists/".length());
                JSONObject model = appModel();
                JSONArray old = storedLists(model);
                JSONArray next = new JSONArray();
                JSONObject result = null;

                for (int i = 0; i < old.length(); i++) {
                    JSONObject item = old.optJSONObject(i);
                    if (item == null) continue;
                    if (item.optString("id").equals(id)) {
                        if (method == Method.DELETE) {
                            result = item;
                            continue;
                        }
                        if (method != Method.PATCH && method != Method.PUT)
                            return json(Response.Status.METHOD_NOT_ALLOWED, error("Método não permitido."));
                        result = fromPanel(body(session), item);
                        result.put("id", id);
                        next.put(result);
                    } else {
                        next.put(item);
                    }
                }

                if (result == null) return json(Response.Status.NOT_FOUND, error("Lista não encontrada."));
                saveLists(model, next);
                if (method == Method.DELETE) return json(Response.Status.OK, new JSONObject().put("ok", true));
                return json(Response.Status.OK, new JSONObject().put("ok", true).put("playlist", toPanel(result)));
            }

            return json(Response.Status.NOT_FOUND, error("Rota não encontrada."));
        } catch (Exception exception) {
            try {
                return json(Response.Status.BAD_REQUEST, error(exception.getMessage() == null
                        ? "Requisição inválida." : exception.getMessage()));
            } catch (Exception ignored) {
                return newFixedLengthResponse("Erro");
            }
        }
    }

    private JSONObject fromPanel(JSONObject input, JSONObject old) throws Exception {
        String type = input.optString("type", old == null ? "" : old.optString("type")).toLowerCase(java.util.Locale.ROOT);
        String name = input.optString("name", old == null ? "" : old.optString("name")).trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Informe o nome da lista.");
        if (!type.equals("m3u") && !type.equals("xtream")) throw new IllegalArgumentException("Tipo de lista inválido.");

        String url;
        if (type.equals("m3u")) {
            url = input.optString("m3u_url", "");
            if (url.isEmpty() && old != null) url = old.optString("url", "");
            checkUrl(url);
        } else {
            String server = input.optString("server_url", "");
            String username = input.optString("username", "");
            String password = input.optString("password", "");
            if (old != null) {
                JSONObject previous = toPanel(old);
                if (server.isEmpty()) server = previous.optString("server_url", "");
                if (username.isEmpty()) username = previous.optString("username", "");
                if (password.isEmpty()) password = previous.optString("password", "");
            }
            checkUrl(server);
            if (username.trim().isEmpty() || password.trim().isEmpty())
                throw new IllegalArgumentException("Informe usuário e senha Xtream.");
            String base = server.trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            url = base + "/get.php?username=" + URLEncoder.encode(username, "UTF-8")
                    + "&password=" + URLEncoder.encode(password, "UTF-8")
                    + "&type=m3u_plus&output=ts";
        }
        return new JSONObject().put("id", old == null ? "" : old.optString("id"))
                .put("name", name).put("type", type).put("url", url).put("is_protected", "0");
    }

    private JSONObject toPanel(JSONObject stored) throws Exception {
        String type = stored.optString("type", "m3u").toLowerCase(java.util.Locale.ROOT);
        String url = stored.optString("url", "");
        JSONObject item = new JSONObject().put("id", stored.optString("id"))
                .put("name", stored.optString("name", "Minha lista")).put("type", type);
        if (type.equals("m3u")) {
            item.put("m3u_url", url);
        } else {
            URI uri = new URI(url);
            String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
            String username = queryParam(query, "username");
            String password = queryParam(query, "password");
            String base = uri.getScheme() + "://" + uri.getRawAuthority();
            item.put("server_url", base).put("username", username).put("password", password);
        }
        return item;
    }

    private String queryParam(String query, String name) throws Exception {
        for (String part : query.split("&")) {
            int index = part.indexOf('=');
            if (index > 0 && part.substring(0, index).equals(name))
                return URLDecoder.decode(part.substring(index + 1), "UTF-8");
        }
        return "";
    }

    private void checkUrl(String value) throws Exception {
        URI uri = new URI(value == null ? "" : value.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) throw new IllegalArgumentException("URL HTTP/HTTPS inválida.");
    }

    private byte[] read(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
