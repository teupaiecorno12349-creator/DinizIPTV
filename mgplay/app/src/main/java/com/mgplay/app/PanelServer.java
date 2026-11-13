package com.mgplay.app;

import android.content.Context;

import fi.iki.elonen.NanoHTTPD;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PanelServer extends NanoHTTPD {
    public static final int PORT = 8765;
    private final Context context;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    public PanelServer(Context context) {
        super("0.0.0.0", PORT);
        this.context = context.getApplicationContext();
    }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<java.net.InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    @Override public Response serve(IHTTPSession session) {
        try {
            String path = session.getUri();
            Method method = session.getMethod();
            if (method == Method.GET && (path.equals("/") || path.equals("/index.html"))) return html();
            if (method == Method.GET && path.equals("/health")) return json(Response.Status.OK, new JSONObject().put("ok", true).put("app", "MG Play"));
            if (method == Method.POST && path.equals("/api/login")) return login(readBody(session));

            String token = bearer(session);
            if (!valid(token)) return error(Response.Status.UNAUTHORIZED, "Sessão inválida. Entre novamente.");
            if (method == Method.GET && path.equals("/api/device")) {
                JSONObject device = new JSONObject().put("mac", DeviceIdentity.mac(context))
                    .put("panel", "http://" + localIp() + ":" + PORT + "/");
                return json(Response.Status.OK, new JSONObject().put("ok", true).put("device", device));
            }
            if (method == Method.GET && path.equals("/api/playlists")) {
                return json(Response.Status.OK, new JSONObject().put("ok", true).put("playlists", PlaylistStore.get(context)));
            }
            if (method == Method.POST && path.equals("/api/playlists")) {
                JSONObject item = makePlaylist(readBody(session));
                item.put("id", UUID.randomUUID().toString());
                JSONArray lists = PlaylistStore.get(context);
                lists.put(item);
                PlaylistStore.save(context, lists);
                return json(Response.Status.CREATED, new JSONObject().put("ok", true).put("playlist", item));
            }
            if (path.startsWith("/api/playlists/")) return mutatePlaylist(session, path.substring("/api/playlists/".length()));
            return error(Response.Status.NOT_FOUND, "Rota não encontrada.");
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Requisição inválida." : e.getMessage();
            return error(Response.Status.BAD_REQUEST, message);
        }
    }

    private Response html() throws Exception {
        InputStream input = context.getAssets().open("admin.html");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        input.close();
        byte[] data = output.toByteArray();
        Response response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", new java.io.ByteArrayInputStream(data), data.length);
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("X-Content-Type-Options", "nosniff");
        return response;
    }

    private Response login(JSONObject body) throws Exception {
        String mac = body.optString("mac", "").trim().toUpperCase(Locale.ROOT).replace('-', ':');
        String key = body.optString("device_key", body.optString("key", ""));
        String expectedMac = DeviceIdentity.mac(context);
        String expectedHash = DeviceIdentity.keyHash(context);
        if (!expectedMac.equals(mac) || !DeviceIdentity.matches(expectedHash, DeviceIdentity.sha256(key))) {
            return error(Response.Status.UNAUTHORIZED, "MAC ou chave do MG Play inválidos.");
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        StringBuilder token = new StringBuilder();
        for (byte b : random) token.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        sessions.put(token.toString(), System.currentTimeMillis() + 12L * 60L * 60L * 1000L);
        JSONObject device = new JSONObject().put("mac", expectedMac).put("name", "MG Play");
        return json(Response.Status.OK, new JSONObject().put("ok", true)
            .put("access_token", token.toString()).put("expires_in", 43200).put("device", device));
    }

    private Response mutatePlaylist(IHTTPSession session, String rawId) throws Exception {
        String id = java.net.URLDecoder.decode(rawId, "UTF-8");
        JSONArray old = PlaylistStore.get(context);
        JSONArray next = new JSONArray();
        JSONObject changed = null;
        JSONObject body = session.getMethod() == Method.DELETE ? new JSONObject() : readBody(session);
        for (int i = 0; i < old.length(); i++) {
            JSONObject existing = old.getJSONObject(i);
            if (!id.equals(existing.optString("id"))) {
                next.put(existing);
                continue;
            }
            if (session.getMethod() == Method.DELETE) {
                changed = existing;
                continue;
            }
            if (session.getMethod() != Method.PUT && session.getMethod() != Method.PATCH) {
                return error(Response.Status.METHOD_NOT_ALLOWED, "Método não permitido.");
            }
            changed = makePlaylist(body, existing);
            changed.put("id", id);
            next.put(changed);
        }
        if (changed == null) return error(Response.Status.NOT_FOUND, "Lista não encontrada.");
        PlaylistStore.save(context, next);
        if (session.getMethod() == Method.DELETE) return json(Response.Status.OK, new JSONObject().put("ok", true));
        return json(Response.Status.OK, new JSONObject().put("ok", true).put("playlist", changed));
    }

    private JSONObject makePlaylist(JSONObject body) throws Exception { return makePlaylist(body, null); }

    private JSONObject makePlaylist(JSONObject body, JSONObject old) throws Exception {
        String type = body.optString("type", old == null ? "" : old.optString("type")).trim().toLowerCase(Locale.ROOT);
        String name = body.optString("name", old == null ? "" : old.optString("name")).trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Informe o nome da lista.");
        if (!type.equals("xtream") && !type.equals("m3u")) throw new IllegalArgumentException("Escolha Xtream Codes ou M3U.");
        JSONObject result = new JSONObject().put("name", name).put("type", type);
        if (type.equals("m3u")) {
            String url = body.optString("m3u_url", old == null ? "" : old.optString("m3u_url")).trim();
            checkUrl(url);
            result.put("m3u_url", url);
        } else {
            String server = body.optString("server_url", old == null ? "" : old.optString("server_url")).trim();
            String username = body.optString("username", old == null ? "" : old.optString("username")).trim();
            String password = body.optString("password", old == null ? "" : old.optString("password"));
            checkUrl(server);
            if (username.isEmpty() || password.isEmpty()) throw new IllegalArgumentException("Informe usuário e senha Xtream.");
            result.put("server_url", server).put("username", username).put("password", password);
        }
        return result;
    }

    private void checkUrl(String value) throws Exception {
        URI uri = new URI(value);
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Use uma URL HTTP ou HTTPS válida.");
        }
    }

    private JSONObject readBody(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String raw = files.get("postData");
        return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private String bearer(IHTTPSession session) {
        String value = session.getHeaders().get("authorization");
        return value != null && value.toLowerCase(Locale.ROOT).startsWith("bearer ") ? value.substring(7).trim() : "";
    }

    private boolean valid(String token) {
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) { sessions.remove(token); return false; }
        return true;
    }

    private Response error(Response.IStatus status, String message) {
        JSONObject body = new JSONObject();
        try {
            body.put("ok", false);
            body.put("error", message);
        } catch (Exception ignored) {}
        return json(status, body);
    }

    private Response json(Response.IStatus status, JSONObject body) {
        Response response = newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString());
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("X-Content-Type-Options", "nosniff");
        return response;
    }
}