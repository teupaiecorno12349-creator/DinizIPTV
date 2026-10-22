package com.diniz.iptv.player;

import android.content.Context;
import java.util.*;
import java.util.concurrent.*;

public final class CatalogLoader {
    public interface Listener {
        void onProgress(int finished,int total,String message);
        void onComplete();
        void onError(String message);
    }
    private final Context context;
    private final XtreamClient api;
    private final Listener listener;
    private final ExecutorService runner=Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;

    public CatalogLoader(Context c,XtreamClient a,Listener l){context=c.getApplicationContext();api=a;listener=l;}
    public void start(){runner.execute(this::load);}
    public void cancel(){cancelled=true;runner.shutdownNow();}

    private void progress(int done,int total,String msg){
        if(listener!=null)listener.onProgress(done,total,msg);
    }
    private void load(){
        try{
            CatalogStore.clear();
            progress(0,6,"Validando acesso Xtream Codes…");
            api.login();
            progress(1,6,"Baixando categorias e canais…");
            List<CatalogCategory> liveCats=api.categories("live");
            List<CatalogCategory> movieCats=api.categories("movie");
            List<CatalogCategory> seriesCats=api.categories("series");
            CatalogStore.CATEGORIES.put("live",liveCats);
            CatalogStore.CATEGORIES.put("movie",movieCats);
            CatalogStore.CATEGORIES.put("series",seriesCats);

            progress(2,6,"Baixando lista completa de canais…");
            List<ContentItem> live=api.streams("live");
            List<ContentItem> movies;
            List<ContentItem> series;
            progress(3,6,"Baixando lista completa de filmes…");
            movies=api.streams("movie");
            progress(4,6,"Baixando lista completa de séries…");
            series=api.streams("series");
            applyCategoryNames(live,liveCats);applyCategoryNames(movies,movieCats);applyCategoryNames(series,seriesCats);
            synchronized(CatalogStore.class){
                CatalogStore.CHANNELS.addAll(live);
                CatalogStore.MOVIES.addAll(movies);
                CatalogStore.SERIES.addAll(series);
            }
            if(cancelled)return;

            List<ContentItem> details=new ArrayList<>(movies.size()+series.size());
            details.addAll(movies);details.addAll(series);
            int total=details.size();
            progress(0,total,"Catálogo pronto; buscando sinopses, anos, capas e episódios…");
            if(total>0){
                ExecutorService pool=Executors.newFixedThreadPool(3);
                CompletionService<Boolean> completion=new ExecutorCompletionService<>(pool);
                for(ContentItem x:details){
                    completion.submit(()->{
                        if(cancelled)return false;
                        try{
                            if("movie".equals(x.type))api.enrichMovie(x);
                            else {
                                List<ContentItem> eps=api.enrichSeries(x);
                                synchronized(CatalogStore.EPISODES){CatalogStore.EPISODES.put(x.id,eps);}
                            }
                        }catch(Exception ignored){
                            // Keep metadata supplied by the base listing if a detail endpoint is absent/throttled.
                        }
                        try{Thread.sleep(65L);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                        return true;
                    });
                }
                for(int i=1;i<=total;i++){
                    if(cancelled){pool.shutdownNow();return;}
                    try{completion.take().get();}catch(Exception ignored){}
                    if(i==total||i%3==0)progress(i,total,"Sinopses e episódios: "+i+" de "+total);
                }
                pool.shutdown();
                pool.awaitTermination(15,TimeUnit.SECONDS);
            }
            if(cancelled)return;
            progress(1,1,"Guardando catálogo no dispositivo…");
            try{CatalogStore.save(context);}catch(Exception ignored){}
            if(listener!=null)listener.onComplete();
        }catch(Exception e){
            if(!cancelled&&listener!=null){
                String message=e.getMessage()==null?"Falha ao carregar o catálogo Xtream Codes.":e.getMessage();
                listener.onError(message);
            }
        }finally{runner.shutdown();}
    }

    private static void applyCategoryNames(List<ContentItem> items,List<CatalogCategory> categories){
        Map<String,String> names=new HashMap<>();
        for(CatalogCategory c:categories)names.put(c.id,c.name);
        for(ContentItem x:items)if(x.categoryName==null||x.categoryName.isEmpty())x.categoryName=names.getOrDefault(x.categoryId,"Sem categoria");
    }
}