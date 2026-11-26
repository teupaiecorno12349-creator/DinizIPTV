package com.diniz.iptv.player;

import android.net.Uri;

import org.json.JSONObject;

import java.io.IOException;

public final class MarcinApi {
    public static final String PANEL_BASE = "https://track-hop-coin-chaser.lovable.app";
    private MarcinApi() {}

    public static JSONObject login(String username, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        JSONObject result = post("/api/public/player/login", body);

        JSONObject provider = result.optJSONObject("provider");
        if (provider == null) throw new IOException("O painel não retornou os dados do fornecedor.");
        String base = provider.optString("base_url", "").trim();
        Uri uri = Uri.parse(base);
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null ||
                !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IOException("O endereço do fornecedor está inválido no painel.");
        }
        String status = provider.optString("status", "");
        if (!status.isEmpty() && !("active".equalsIgnoreCase(status) ||
                "enabled".equalsIgnoreCase(status))) {
            throw new IOException("A conta do fornecedor não está ativa.");
        }
        return result;
    }

    public static JSONObject catalog(String username, String password, String action) throws Exception {
        return catalog(username, password, action, null, null);
    }

    public static JSONObject catalog(String username, String password, String action,
                                      String idKey, String id) throws Exception {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        body.put("action", action);
        if (idKey != null && id != null) body.put(idKey, id);
        return post("/api/public/player/catalog", body);
    }

    private static JSONObject post(String path, JSONObject payload) throws Exception {
        JSONObject result = Net.postJson(PANEL_BASE + path, payload);
        if (!result.optBoolean("ok", false)) {
            throw new IOException(messageFor(result.optString("error", "service_unavailable")));
        }
        return result;
    }

    public static String messageFor(String code) {
        switch (code) {
            case "missing_credentials":
            case "invalid_request":
                return "Dados de acesso ou solicitação inválidos.";
            case "invalid_credentials":
                return "Usuário ou senha não conferem.";
            case "provider_invalid_credentials":
                return "O fornecedor recusou os dados desta conta.";
            case "blocked":
                return "Esta conta está bloqueada. Procure o administrador.";
            case "expired":
                return "Seu acesso venceu. Procure o administrador para renovar.";
            case "provider_inactive":
                return "A conta está inativa ou vencida no fornecedor.";
            case "provider_not_configured":
                return "O servidor de conteúdo ainda não foi configurado.";
            case "service_unavailable":
                return "O painel está temporariamente indisponível.";
            case "provider_unavailable":
                return "O fornecedor está indisponível. Tente novamente.";
            default:
                return "Não foi possível conectar. Verifique a internet e tente novamente.";
        }
    }
}
