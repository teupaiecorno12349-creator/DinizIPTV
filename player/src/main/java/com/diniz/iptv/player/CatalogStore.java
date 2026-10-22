package com.diniz.iptv.player;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class CatalogStore {
    private CatalogStore() {}
    public static final List<ContentItem> CHANNELS=new ArrayList<>();
    public static final List<ContentItem> MOVIES=new ArrayList<>();
    public static final List<ContentItem> SERIES=new ArrayList<>();
    public static final Map<Long,List<ContentItem>> EPISODES=new LinkedHashMap<>();
    public static final Map<String,List<CatalogCategory>> CATEGORIES=new LinkedHashMap<>();
    private static final String FILE="marcin-catalog-v1.json.gz";

    public static synchronized void clear(){CHANNELS.clear();MOVIES.clear();SERIES.clear();EPISODES.clear();CATEGORIES.clear();}
    public static synchronized List<ContentItem> list(String type){return "live".equals(type)?CHANNELS:("series".equals(type)?SERIES:MOVIES);}
    public static synchronized void save(Context c)throws Exception{
        JSONObject root=new JSONObject();root.put("version",1);root.put("channels",items(CHANNELS));root.put("movies",items(MOVIES));root.put("series",items(SERIES));
        JSONObject cats=new JSONObject();
        for(String type:CATEGORIES.keySet()){JSONArray a=new JSONArray();for(CatalogCategory x:CATEGORIES.get(type))a.put(x.toJson());cats.put(type,a);}
        root.put("categories",cats);JSONObject ep=new JSONObject();
        for(Long id:EPISODES.keySet())ep.put(Long.toString(id),items(EPISODES.get(id)));root.put("episodes",ep);
        try(GZIPOutputStream out=new GZIPOutputStream(new FileOutputStream(new File(c.getFilesDir(),FILE)))){
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    public static synchronized void clearCache(Context c){
        clear();
        File f=new File(c.getFilesDir(),FILE);if(f.exists())f.delete();
        File posters=new File(c.getCacheDir(),"posters");
        deleteRecursively(posters);
    }
    private static void deleteRecursively(File f){
        if(f==null||!f.exists())return;
        File[] children=f.listFiles();if(children!=null)for(File child:children)deleteRecursively(child);
        f.delete();
    }
    public static synchronized boolean load(Context c){
        File f=new File(c.getFilesDir(),FILE);if(!f.isFile())return false;
        try(GZIPInputStream in=new GZIPInputStream(new FileInputStream(f));InputStreamReader reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            StringBuilder sb=new StringBuilder();char[] buf=new char[8192];int n;while((n=reader.read(buf))>0)sb.append(buf,0,n);
            JSONObject root=new JSONObject(sb.toString());clear();
            read(root.optJSONArray("channels"),CHANNELS);read(root.optJSONArray("movies"),MOVIES);read(root.optJSONArray("series"),SERIES);
            JSONObject cats=root.optJSONObject("categories");
            if(cats!=null){Iterator<String> it=cats.keys();while(it.hasNext()){
                String type=it.next();JSONArray a=cats.optJSONArray(type);List<CatalogCategory> dest=new ArrayList<>();
                if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)dest.add(new CatalogCategory(o.optString("id"),o.optString("name")));}
                CATEGORIES.put(type,dest);
            }}
            JSONObject eps=root.optJSONObject("episodes");
            if(eps!=null){Iterator<String> it=eps.keys();while(it.hasNext()){
                String key=it.next();List<ContentItem> list=new ArrayList<>();read(eps.optJSONArray(key),list);EPISODES.put(Long.parseLong(key),list);
            }}
            return !CHANNELS.isEmpty()||!MOVIES.isEmpty()||!SERIES.isEmpty();
        }catch(Exception e){return false;}
    }
    private static JSONArray items(List<ContentItem> list)throws Exception{JSONArray a=new JSONArray();for(ContentItem x:list)a.put(x.toJson());return a;}
    private static void read(JSONArray a,List<ContentItem> dest){if(a==null)return;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)dest.add(ContentItem.fromJson(o));}}
}