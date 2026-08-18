package com.diniz.iptv.player;

import org.json.JSONException;
import org.json.JSONObject;

public class ContentItem {
    public long id;
    public String type, title, episodeTitle, year, description, posterUrl, genres, streamUrl;
    public int season, episode;

    public static ContentItem fromJson(JSONObject o) throws JSONException {
        ContentItem x = new ContentItem();
        x.id = o.optLong("id");
        x.type = o.optString("type", "movie");
        x.title = o.optString("title");
        x.episodeTitle = o.optString("episodeTitle");
        x.year = o.optString("year");
        x.description = o.optString("description");
        x.posterUrl = o.optString("posterUrl");
        x.genres = o.optString("genres");
        x.streamUrl = o.optString("streamUrl");
        x.season = o.optInt("season", 0);
        x.episode = o.optInt("episode", 0);
        return x;
    }

    public String displayTitle() {
        if (year == null || year.isEmpty()) return title;
        return title + " (" + year + ")";
    }
}
