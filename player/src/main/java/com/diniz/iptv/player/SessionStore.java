package com.diniz.iptv.player;

import android.net.Uri;

public final class SessionStore {
    private static String username;
    private static String password;
    private static String providerBase;

    private SessionStore() {}

    public static synchronized void set(String user, String pass, String base) {
        username = user;
        password = pass;
        providerBase = trimSlash(base);
    }

    public static synchronized boolean isActive() {
        return username != null && !username.isEmpty()
                && password != null && !password.isEmpty()
                && providerBase != null && !providerBase.isEmpty();
    }

    public static synchronized String username() { return username; }
    public static synchronized String password() { return password; }
    public static synchronized String providerBase() { return providerBase; }

    public static synchronized void updateProviderBase(String base) {
        providerBase = trimSlash(base);
    }

    public static synchronized void clear() {
        username = null;
        password = null;
        providerBase = null;
        CatalogStore.ITEMS.clear();
    }

    public static synchronized String playbackUrl(String kind, String id, String extension) {
        if (!isActive()) throw new IllegalStateException("Faça login novamente.");
        if (!("live".equals(kind) || "movie".equals(kind) || "series".equals(kind))) {
            throw new IllegalArgumentException("Tipo de reprodução inválido.");
        }
        if (id == null || !id.matches("[0-9]+")) throw new IllegalArgumentException("ID de conteúdo inválido.");
        if (extension == null || !extension.matches("[A-Za-z0-9]{1,10}")) {
            throw new IllegalArgumentException("Formato de vídeo não informado pelo fornecedor.");
        }
        return Uri.parse(providerBase).buildUpon()
                .appendPath(kind)
                .appendPath(username)
                .appendPath(password)
                .appendPath(id + "." + extension)
                .build().toString();
    }

    private static String trimSlash(String value) {
        if (value == null) return "";
        String out = value.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
