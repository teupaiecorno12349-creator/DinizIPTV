package com.diniz.iptv.player;

public class ContentItem {
    public String id = "";
    public String type = "movie";
    public String title = "";
    public String episodeTitle = "";
    public String year = "";
    public String description = "";
    public String posterUrl = "";
    public String genres = "";
    public String category = "";
    public String categoryId = "";
    public String containerExtension = "";
    public int season;
    public int episode;

    public String displayTitle() {
        if (year == null || year.isEmpty() || "0".equals(year)) return title;
        return title + " (" + year + ")";
    }
}
