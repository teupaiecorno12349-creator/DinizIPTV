package com.diniz.iptv.player;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class Net {
    private Net() {}

    public static JSONObject postJson(String url, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(45000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-store");
        connection.setDoOutput(true);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);

        try {
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder text = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) text.append(line);
                }
            }

            JSONObject result;
            try {
                result = text.length() == 0 ? new JSONObject() : new JSONObject(text.toString());
            } catch (Exception ignored) {
                throw new IOException("O painel retornou uma resposta inválida.");
            }

            if (code < 200 || code >= 300) {
                String error = result.optString("error", "");
                if (error.isEmpty()) {
                    if (code == 401) error = "invalid_credentials";
                    else if (code == 403) error = "blocked";
                    else if (code == 502) error = "provider_unavailable";
                    else if (code == 503) error = "service_unavailable";
                    else error = "network_error";
                }
                throw new IOException(MarcinApi.messageFor(error));
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }
}
