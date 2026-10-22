package com.diniz.iptv.player;

import org.json.JSONObject;

public final class CatalogCategory {
    public final String id;
    public final String name;

    public CatalogCategory(String id, String name) {
        this.id = id == null ? "" : id;
        this.name = name == null || name.trim().isEmpty() ? "Sem categoria" : name.trim();
    }

    public static CatalogCategory fromJson(JSONObject o) {
        return new CatalogCategory(o.optString("category_id", o.optString("id", "")),
                o.optString("category_name", o.optString("name", "Sem categoria")));
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try { o.put("id", id); o.put("name", name); } catch (Exception ignored) {}
        return o;
    }
}
