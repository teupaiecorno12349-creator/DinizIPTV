package com.diniz.iptv.player;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class XtreamClient {
    public static final class LoginResult {
        public final String status, expires, maxConnections;
        LoginResult(String s,String e,String m){status=s;expires=e;maxConnections=m;}
    }
    public final String base, username, password;
    private final Context context;

    public XtreamClient(Context c,String server,String user,String pass) {
        context=c.getApplicationContext();
        base=normalizeBase(server); username=user==null?"":user.trim(); password=pass==null?"":pass;
    }
    public static String normalizeBase(String s) {
        s=s==null?"":s.trim();
        while(s.endsWith("/"))s=s.substring(0,s.length()-1);
        if(s.endsWith("/player_api.php"))s=s.substring(0,s.length()-15);
        if(!s.isEmpty()&&!s.matches("(?i)^https?://.*"))s="http://"+s;
        return s;
    }
    private HttpUrl url(Map<String,String> params) throws IOException {
        HttpUrl u=HttpUrl.parse(base+"/player_api.php");
        if(u==null)throw new IOException("URL do servidor inválida. Use o endereço Xtream Codes completo.");
        HttpUrl.Builder b=u.newBuilder().addQueryParameter("username",username).addQueryParameter("password",password);
        for(Map.Entry<String,String> e:params.entrySet())b.addQueryParameter(e.getKey(),e.getValue());
        return b.build();
    }
    private Object call(Map<String,String> params) throws Exception {
        Request request=new Request.Builder().url(url(params)).get().header("Accept","application/json")
            .header("User-Agent","MarcinIPTV/1.0 (Android TV)").build();
        try(Response response=NetworkStack.client(context).newCall(request).execute()){
            ResponseBody body=response.body();String text=body==null?"":body.string();
            if(!response.isSuccessful())throw new IOException("Servidor respondeu HTTP "+response.code());
            if(text.trim().isEmpty())throw new IOException("Resposta vazia do servidor.");
            try {
                if(text.trim().startsWith("["))return new JSONArray(text);
                JSONObject o=new JSONObject(text);
                if(o.has("error")&&!o.isNull("error"))throw new IOException(o.optString("error","Erro do servidor Xtream"));
                return o;
            } catch(org.json.JSONException e) { throw new IOException("Resposta inválida da API Xtream Codes."); }
        }
    }
    public LoginResult login() throws Exception {
        if(base.isEmpty()||username.isEmpty()||password.isEmpty())throw new IOException("Preencha URL, usuário e senha.");
        Object obj=call(Collections.emptyMap());
        if(!(obj instanceof JSONObject))throw new IOException("A URL não retornou uma API Xtream Codes.");
        JSONObject root=(JSONObject)obj,user=root.optJSONObject("user_info");
        if(user==null)throw new IOException("API Xtream Codes não encontrada. Confira a URL e a porta.");
        String auth=user.optString("auth","1"),status=user.optString("status","Active");
        if(!"1".equals(auth)&&!"true".equalsIgnoreCase(auth))throw new IOException("Usuário ou senha inválidos.");
        String sl=status.toLowerCase(Locale.ROOT);
        if(sl.contains("expired")||sl.contains("disabled")||sl.contains("banned"))throw new IOException("A conta está "+status+".");
        return new LoginResult(status,user.optString("exp_date",""),user.optString("max_connections",""));
    }
    public JSONArray array(String action) throws Exception {
        Map<String,String> p=new HashMap<>();p.put("action",action);
        Object o=call(p);if(o instanceof JSONArray)return (JSONArray)o;
        throw new IOException("Resposta inesperada ao carregar "+action+".");
    }
    public List<CatalogCategory> categories(String type) throws Exception {
        String action="live".equals(type)?"get_live_categories":("series".equals(type)?"get_series_categories":"get_vod_categories");
        JSONArray a=array(action);List<CatalogCategory> out=new ArrayList<>();
        for(int i=0;i<a.length();i++)out.add(CatalogCategory.fromJson(a.getJSONObject(i)));
        return out;
    }
    public List<ContentItem> streams(String type) throws Exception {
        String action="live".equals(type)?"get_live_streams":("series".equals(type)?"get_series":"get_vod_streams");
        JSONArray a=array(action);List<ContentItem> out=new ArrayList<>(a.length());
        for(int i=0;i<a.length();i++){
            JSONObject o=a.getJSONObject(i);ContentItem x=new ContentItem();
            x.type=type;x.id=o.optLong("stream_id",o.optLong("series_id",o.optLong("id")));
            x.title=first(o,"name","title");if(x.title.isEmpty())x.title="Sem título";
            x.posterUrl=asset(first(o,"stream_icon","cover","cover_big","movie_image"));
            x.backdropUrl=asset(first(o,"backdrop_path","cover","cover_big"));
            x.categoryId=o.optString("category_id","");x.categoryName=o.optString("category_name","");
            x.rating=first(o,"rating","rating_5based");x.added=o.optString("added","");
            x.containerExtension=first(o,"container_extension");if(x.containerExtension.isEmpty())x.containerExtension="live".equals(type)?"ts":"mp4";
            x.year=year(first(o,"year","releaseDate","releasedate","release_date"));
            x.genres=first(o,"genre","genres");x.description=first(o,"plot","description","overview");
            x.director=first(o,"director");x.cast=first(o,"cast");x.duration=first(o,"duration");
            x.streamUrl=streamUrl(type,x.id,x.containerExtension);
            out.add(x);
        }
        return out;
    }
    public void enrichMovie(ContentItem x) throws Exception {
        Map<String,String> p=new HashMap<>();p.put("action","get_vod_info");p.put("vod_id",Long.toString(x.id));
        Object result=call(p);if(!(result instanceof JSONObject))return;JSONObject root=(JSONObject)result;
        JSONObject info=root.optJSONObject("info");if(info==null)info=root.optJSONObject("movie_data");if(info==null)return;
        assign(x,"description",first(info,"plot","description","overview"));assign(x,"genres",first(info,"genre","genres"));
        assign(x,"year",year(first(info,"year","releaseDate","release_date","releasedate")));
        assign(x,"director",first(info,"director"));assign(x,"cast",first(info,"cast"));
        assign(x,"duration",first(info,"duration","duration_secs"));assign(x,"rating",first(info,"rating","rating_5based"));
        String poster=asset(first(info,"movie_image","cover_big","cover","stream_icon"));if(!poster.isEmpty())x.posterUrl=poster;
        String backdrop=asset(first(info,"backdrop_path","backdrop"));if(!backdrop.isEmpty())x.backdropUrl=backdrop;
        JSONObject data=root.optJSONObject("movie_data");
        if(data!=null){String ext=first(data,"container_extension");if(!ext.isEmpty())x.containerExtension=ext;}
        x.streamUrl=streamUrl("movie",x.id,x.containerExtension);
    }
    public List<ContentItem> enrichSeries(ContentItem x) throws Exception {
        Map<String,String> p=new HashMap<>();p.put("action","get_series_info");p.put("series_id",Long.toString(x.id));
        Object result=call(p);if(!(result instanceof JSONObject))return Collections.emptyList();
        JSONObject root=(JSONObject)result,info=root.optJSONObject("info");
        if(info!=null){
            assign(x,"description",first(info,"plot","description","overview"));assign(x,"genres",first(info,"genre","genres"));
            assign(x,"year",year(first(info,"year","releaseDate","release_date","releasedate")));
            assign(x,"director",first(info,"director"));assign(x,"cast",first(info,"cast"));assign(x,"rating",first(info,"rating","rating_5based"));
            String poster=asset(first(info,"cover","cover_big","stream_icon","movie_image"));if(!poster.isEmpty())x.posterUrl=poster;
            String backdrop=asset(first(info,"backdrop_path","backdrop"));if(!backdrop.isEmpty())x.backdropUrl=backdrop;
            String name=first(info,"name");if(!name.isEmpty())x.title=name;
        }
        List<ContentItem> eps=new ArrayList<>();JSONObject seasons=root.optJSONObject("episodes");
        if(seasons!=null){
            Iterator<String> it=seasons.keys();
            while(it.hasNext()){
                String key=it.next();JSONArray a=seasons.optJSONArray(key);if(a==null)continue;int sn=integer(key,0);
                for(int i=0;i<a.length();i++){
                    JSONObject ep=a.optJSONObject(i);if(ep==null)continue;
                    ContentItem e=new ContentItem();e.type="episode";e.id=ep.optLong("id",ep.optLong("episode_id"));
                    e.seriesId=x.id;e.title=x.title;e.episodeTitle=first(ep,"title","name","episode_title");
                    e.season=ep.optInt("season",sn);e.episode=ep.optInt("episode_num",ep.optInt("episode",i+1));
                    JSONObject ei=ep.optJSONObject("info");
                    if(ei!=null){e.description=first(ei,"plot","description","overview");e.duration=first(ei,"duration","duration_secs");
                        e.posterUrl=asset(first(ei,"movie_image","cover_big"));}
                    e.containerExtension=first(ep,"container_extension");if(e.containerExtension.isEmpty())e.containerExtension="mp4";
                    e.streamUrl=streamUrl("series",e.id,e.containerExtension);e.categoryId=x.categoryId;e.categoryName=x.categoryName;
                    e.genres=x.genres;e.year=x.year;eps.add(e);
                }
            }
        }
        eps.sort((a,b)->{int c=Integer.compare(a.season,b.season);return c!=0?c:Integer.compare(a.episode,b.episode);});
        return eps;
    }
    private String streamUrl(String type,long id,String ext) {
        String k="live".equals(type)?"live":("series".equals(type)?"series":"movie");
        if(ext==null||ext.trim().isEmpty())ext="live".equals(type)?"ts":"mp4";
        return base+"/"+k+"/"+Uri.encode(username)+"/"+Uri.encode(password)+"/"+id+"."+ext;
    }
    private String asset(String p) {
        if(p==null||p.trim().isEmpty())return "";p=p.trim();
        if(p.startsWith("//"))return "https:"+p;if(p.matches("(?i)^https?://.*"))return p;
        if(p.startsWith("/"))return base+p;return base+"/"+p;
    }
    private static String first(JSONObject o,String...keys){for(String k:keys){String v=o.optString(k,"");if(v!=null&&!v.trim().isEmpty()&&!"null".equalsIgnoreCase(v))return v.trim();}return "";}
    private static String year(String s){if(s==null)return "";java.util.regex.Matcher m=java.util.regex.Pattern.compile("(19\\d{2}|20\\d{2})").matcher(s);return m.find()?m.group(1):"";}
    private static int integer(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private static void assign(ContentItem x,String key,String v){if(v==null||v.trim().isEmpty())return;switch(key){
        case "description":x.description=v;break;case "genres":x.genres=v;break;case "year":x.year=v;break;
        case "director":x.director=v;break;case "cast":x.cast=v;break;case "duration":x.duration=v;break;case "rating":x.rating=v;break;
    }}
}