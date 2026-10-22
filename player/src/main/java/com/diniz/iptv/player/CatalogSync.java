package com.diniz.iptv.player;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONArray;

public final class CatalogSync {
    private static final String STATE="marcin_catalog_state";
    private CatalogSync(){}

    public static void markRefreshed(Context context,String tab){
        long now=System.currentTimeMillis();
        SharedPreferences.Editor e=context.getApplicationContext().getSharedPreferences(STATE,Context.MODE_PRIVATE).edit();
        e.putLong("last_successful_sync",now);
        if(tab==null||"all".equals(tab)){
            e.putLong("last_full_refresh",now);
            e.putLong("last_refresh_live",now);
            e.putLong("last_refresh_movie",now);
            e.putLong("last_refresh_series",now);
        }else{
            e.putLong("last_refresh_"+tab,now);
        }
        e.apply();
    }

    public static long lastFullRefresh(Context context){
        return context.getApplicationContext().getSharedPreferences(STATE,Context.MODE_PRIVATE).getLong("last_full_refresh",0L);
    }

    public static void refreshAll(Context context,XtreamClient api)throws Exception{
        Context c=context.getApplicationContext();
        loadCacheIfNeeded(c);
        api.login();
        List<CatalogCategory> liveCats=api.categories("live");
        List<CatalogCategory> movieCats=api.categories("movie");
        List<CatalogCategory> seriesCats=api.categories("series");
        List<ContentItem> live=api.streams("live");
        List<ContentItem> movies=api.streams("movie");
        List<ContentItem> series=api.streams("series");
        applyCategoryNames(live,liveCats);
        applyCategoryNames(movies,movieCats);
        applyCategoryNames(series,seriesCats);
        synchronized(CatalogStore.class){
            Map<String,ContentItem> old=new HashMap<>();
            remember(old,CatalogStore.MOVIES);remember(old,CatalogStore.SERIES);
            for(ContentItem item:movies)inherit(item,old.get(item.key()));
            for(ContentItem item:series)inherit(item,old.get(item.key()));
            preserveEpisodes(series);
            CatalogStore.CHANNELS.clear();CatalogStore.CHANNELS.addAll(live);
            CatalogStore.MOVIES.clear();CatalogStore.MOVIES.addAll(movies);
            CatalogStore.SERIES.clear();CatalogStore.SERIES.addAll(series);
            CatalogStore.CATEGORIES.clear();
            CatalogStore.CATEGORIES.put("live",liveCats);
            CatalogStore.CATEGORIES.put("movie",movieCats);
            CatalogStore.CATEGORIES.put("series",seriesCats);
        }
        CatalogStore.save(c);
        markRefreshed(c,"all");
    }

    public static void refreshTab(Context context,XtreamClient api,String tab)throws Exception{
        Context c=context.getApplicationContext();
        if(!"live".equals(tab)&&!"movie".equals(tab)&&!"series".equals(tab))throw new IllegalArgumentException("Aba inválida.");
        loadCacheIfNeeded(c);
        api.login();
        List<CatalogCategory> categories=api.categories(tab);
        List<ContentItem> fresh=api.streams(tab);
        applyCategoryNames(fresh,categories);
        synchronized(CatalogStore.class){
            Map<String,ContentItem> old=new HashMap<>();
            remember(old,CatalogStore.list(tab));
            for(ContentItem item:fresh)inherit(item,old.get(item.key()));
            if("live".equals(tab)){CatalogStore.CHANNELS.clear();CatalogStore.CHANNELS.addAll(fresh);}
            else if("movie".equals(tab)){CatalogStore.MOVIES.clear();CatalogStore.MOVIES.addAll(fresh);}
            else {CatalogStore.SERIES.clear();CatalogStore.SERIES.addAll(fresh);preserveEpisodes(fresh);}
            CatalogStore.CATEGORIES.put(tab,categories);
        }
        CatalogStore.save(c);
        markRefreshed(c,tab);
    }

    private static void loadCacheIfNeeded(Context c){
        synchronized(CatalogStore.class){
            if(CatalogStore.CHANNELS.isEmpty()&&CatalogStore.MOVIES.isEmpty()&&CatalogStore.SERIES.isEmpty())CatalogStore.load(c);
        }
    }
    private static void preserveEpisodes(List<ContentItem> series){
        Set<Long> ids=new HashSet<>();for(ContentItem x:series)ids.add(x.id);
        synchronized(CatalogStore.EPISODES){
            Map<Long,List<ContentItem>> saved=new LinkedHashMap<>();
            for(Map.Entry<Long,List<ContentItem>> entry:CatalogStore.EPISODES.entrySet())
                if(ids.contains(entry.getKey()))saved.put(entry.getKey(),entry.getValue());
            CatalogStore.EPISODES.clear();CatalogStore.EPISODES.putAll(saved);
        }
    }
    private static void remember(Map<String,ContentItem> dest,List<ContentItem> src){
        for(ContentItem item:src)dest.put(item.key(),item);
    }
    private static void inherit(ContentItem fresh,ContentItem cached){
        if(cached==null)return;
        if(empty(fresh.description))fresh.description=cached.description;
        if(empty(fresh.posterUrl))fresh.posterUrl=cached.posterUrl;
        if(empty(fresh.backdropUrl))fresh.backdropUrl=cached.backdropUrl;
        if(empty(fresh.year))fresh.year=cached.year;
        if(empty(fresh.genres))fresh.genres=cached.genres;
        if(empty(fresh.rating))fresh.rating=cached.rating;
        if(empty(fresh.director))fresh.director=cached.director;
        if(empty(fresh.cast))fresh.cast=cached.cast;
        if(empty(fresh.duration))fresh.duration=cached.duration;
        if(empty(fresh.categoryName))fresh.categoryName=cached.categoryName;
    }
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static void applyCategoryNames(List<ContentItem> items,List<CatalogCategory> categories){
        Map<String,String> names=new HashMap<>();for(CatalogCategory c:categories)names.put(c.id,c.name);
        for(ContentItem item:items)if(empty(item.categoryName))item.categoryName=names.getOrDefault(item.categoryId,"Sem categoria");
    }
}