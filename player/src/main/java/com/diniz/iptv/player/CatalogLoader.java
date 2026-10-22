package com.diniz.iptv.player;

import android.content.Context;
import java.util.*;

public final class CatalogLoader {
    public interface Listener {
        void onProgress(int finished,int total,String message);
        void onComplete();
        void onError(String message);
    }

    private final Context context;
    private final XtreamClient api;
    private final Listener listener;
    private volatile boolean cancelled;
    private Thread worker;

    public CatalogLoader(Context c,XtreamClient a,Listener l){
        context=c.getApplicationContext();
        api=a;
        listener=l;
    }

    public synchronized void start(){
        if(worker!=null)return;
        worker=new Thread(this::load,"Marcin-Catalog-Loader");
        worker.start();
    }

    public synchronized void cancel(){
        cancelled=true;
        if(worker!=null)worker.interrupt();
    }

    private void progress(int done,int total,String msg){
        if(!cancelled&&listener!=null)listener.onProgress(done,total,msg);
    }

    private void load(){
        try{
            progress(0,7,"Validando acesso Xtream Codes…");
            api.login();
            if(cancelled)return;

            progress(1,7,"Consultando categorias…");
            List<CatalogCategory> liveCats=api.categories("live");
            if(cancelled)return;
            List<CatalogCategory> movieCats=api.categories("movie");
            if(cancelled)return;
            List<CatalogCategory> seriesCats=api.categories("series");
            if(cancelled)return;

            progress(4,7,"Listando canais…");
            List<ContentItem> live=api.streams("live");
            if(cancelled)return;
            progress(5,7,"Listando filmes…");
            List<ContentItem> movies=api.streams("movie");
            if(cancelled)return;
            progress(6,7,"Listando séries…");
            List<ContentItem> series=api.streams("series");
            if(cancelled)return;

            /*
             * Xtream get_vod_streams/get_series/get_live_streams already return
             * the bulk catalogue and poster URLs. Never issue get_vod_info or
             * get_series_info for every row at login: those are detail endpoints
             * and can mean thousands of serial requests on large playlists.
             * Fetch full synopsis/credits/episodes only when the user opens an item.
             */
            applyCategoryNames(live,liveCats);
            applyCategoryNames(movies,movieCats);
            applyCategoryNames(series,seriesCats);
            synchronized(CatalogStore.class){
                Map<String,ContentItem> oldMetadata=new HashMap<>();
                remember(oldMetadata,CatalogStore.MOVIES);
                remember(oldMetadata,CatalogStore.SERIES);
                for(ContentItem item:movies)inheritMetadata(item,oldMetadata.get(item.key()));
                for(ContentItem item:series)inheritMetadata(item,oldMetadata.get(item.key()));

                Set<Long> seriesIds=new HashSet<>();
                for(ContentItem item:series)seriesIds.add(item.id);
                Map<Long,List<ContentItem>> oldEpisodes=new LinkedHashMap<>();
                synchronized(CatalogStore.EPISODES){
                    for(Map.Entry<Long,List<ContentItem>> e:CatalogStore.EPISODES.entrySet()){
                        if(seriesIds.contains(e.getKey()))oldEpisodes.put(e.getKey(),e.getValue());
                    }
                    CatalogStore.EPISODES.clear();
                    CatalogStore.EPISODES.putAll(oldEpisodes);
                }

                CatalogStore.CHANNELS.clear();
                CatalogStore.MOVIES.clear();
                CatalogStore.SERIES.clear();
                CatalogStore.CHANNELS.addAll(live);
                CatalogStore.MOVIES.addAll(movies);
                CatalogStore.SERIES.addAll(series);
                CatalogStore.CATEGORIES.clear();
                CatalogStore.CATEGORIES.put("live",liveCats);
                CatalogStore.CATEGORIES.put("movie",movieCats);
                CatalogStore.CATEGORIES.put("series",seriesCats);
            }
            if(cancelled)return;

            progress(7,7,"Salvando catálogo local…");
            CatalogStore.save(context);
            if(cancelled)return;

            if(listener!=null)listener.onComplete();
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            if(!cancelled&&listener!=null)listener.onError("A consulta do catálogo foi interrompida.");
        }catch(Exception e){
            if(!cancelled&&listener!=null){
                String message=e.getMessage()==null?"Falha ao carregar o catálogo Xtream Codes.":e.getMessage();
                listener.onError(message);
            }
        }
    }

    private static void remember(Map<String,ContentItem> dest,List<ContentItem> items){
        for(ContentItem item:items)dest.put(item.key(),item);
    }

    private static void inheritMetadata(ContentItem fresh,ContentItem cached){
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
        Map<String,String> names=new HashMap<>();
        for(CatalogCategory c:categories)names.put(c.id,c.name);
        for(ContentItem x:items){
            if(empty(x.categoryName))x.categoryName=names.getOrDefault(x.categoryId,"Sem categoria");
        }
    }
}
