package com.diniz.iptv.player;

import org.json.JSONException;
import org.json.JSONObject;

public class ContentItem {
    public long id, seriesId;
    public String type="movie", title="", episodeTitle="", year="", description="", posterUrl="", backdropUrl="", genres="", streamUrl="";
    public String categoryId="", categoryName="", rating="", director="", cast="", duration="", containerExtension="", added="";
    public int season, episode;
    public String key(){return type+":"+id;}
    public String displayTitle(){
        String t=title==null?"":title.trim();
        if(year!=null&&!year.trim().isEmpty()&&!t.matches(".*\\(\\d{4}\\)$"))return t+" ("+year.trim()+")";
        return t;
    }
    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("type",type);o.put("title",title);o.put("episodeTitle",episodeTitle);
        o.put("year",year);o.put("description",description);o.put("posterUrl",posterUrl);o.put("backdropUrl",backdropUrl);
        o.put("genres",genres);o.put("streamUrl",streamUrl);o.put("categoryId",categoryId);o.put("categoryName",categoryName);
        o.put("rating",rating);o.put("director",director);o.put("cast",cast);o.put("duration",duration);
        o.put("containerExtension",containerExtension);o.put("added",added);o.put("seriesId",seriesId);o.put("season",season);o.put("episode",episode);
        return o;
    }
    public static ContentItem fromJson(JSONObject o){
        ContentItem x=new ContentItem();
        x.id=o.optLong("id");x.seriesId=o.optLong("seriesId");x.type=o.optString("type","movie");x.title=o.optString("title","");
        x.episodeTitle=o.optString("episodeTitle","");x.year=o.optString("year","");x.description=o.optString("description","");
        x.posterUrl=o.optString("posterUrl","");x.backdropUrl=o.optString("backdropUrl","");x.genres=o.optString("genres","");
        x.streamUrl=o.optString("streamUrl","");x.categoryId=o.optString("categoryId","");x.categoryName=o.optString("categoryName","");
        x.rating=o.optString("rating","");x.director=o.optString("director","");x.cast=o.optString("cast","");x.duration=o.optString("duration","");
        x.containerExtension=o.optString("containerExtension","");x.added=o.optString("added","");x.season=o.optInt("season");x.episode=o.optInt("episode");
        return x;
    }
}