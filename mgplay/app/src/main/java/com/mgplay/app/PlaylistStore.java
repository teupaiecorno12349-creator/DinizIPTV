package com.mgplay.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

public final class PlaylistStore {
    private static final String PREFS = "mgplay_store";
    private static final String LISTS = "playlists_encrypted";
    private PlaylistStore() {}

    public static synchronized JSONArray get(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = p.getString(LISTS, "");
        if (stored.isEmpty()) return new JSONArray();
        try { return new JSONArray(Vault.decrypt(stored)); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static synchronized void save(Context context, JSONArray lists) throws Exception {
        String encrypted = Vault.encrypt(lists.toString());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LISTS, encrypted).apply();
    }

    public static synchronized void saveCatalog(Context context, JSONArray catalog) throws Exception {
        String encrypted = Vault.encrypt(catalog.toString());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("catalog_encrypted", encrypted).apply();
    }

    public static synchronized JSONArray getCatalog(Context context) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("catalog_encrypted", "");
        if (stored.isEmpty()) return new JSONArray();
        try { return new JSONArray(Vault.decrypt(stored)); }
        catch (Exception e) { return new JSONArray(); }
    }
}