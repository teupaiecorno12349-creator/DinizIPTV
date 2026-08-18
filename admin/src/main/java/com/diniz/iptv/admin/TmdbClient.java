package com.diniz.iptv.admin;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class TmdbClient {
    public static class Meta { public String title="", year="", description="", posterUrl="", genres=""; }
    public static Meta fetch(String token,String title,boolean series) throws Exception {
        if(token==null||token.trim().isEmpty())throw new IOException("Configure o TMDb Read Access Token no Admin.");
        String kind=series?"tv":"movie";
        String search="https://api.themoviedb.org/3/search/"+kind+"?language=pt-BR&include_adult=false&query="+URLEncoder.encode(title,"UTF-8");
        JSONObject sr=get(token,search);JSONArray results=sr.optJSONArray("results");if(results==null||results.length()==0)throw new IOException("Título não encontrado no TMDb.");
        JSONObject first=results.getJSONObject(0);long id=first.getLong("id");JSONObject d=get(token,"https://api.themoviedb.org/3/"+kind+"/"+id+"?language=pt-BR");
        Meta m=new Meta();m.title=d.optString(series?"name":"title",title);String date=d.optString(series?"first_air_date":"release_date","");m.year=date.length()>=4?date.substring(0,4):"";m.description=d.optString("overview","");String pp=d.optString("poster_path","");if(!pp.isEmpty()&&!"null".equals(pp))m.posterUrl="https://image.tmdb.org/t/p/w500"+pp;
        JSONArray gs=d.optJSONArray("genres");StringBuilder g=new StringBuilder();if(gs!=null)for(int i=0;i<gs.length();i++){if(i>0)g.append(", ");g.append(gs.getJSONObject(i).optString("name"));}m.genres=g.toString();return m;
    }
    private static JSONObject get(String token,String url) throws Exception {HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("Authorization","Bearer "+token.trim());c.setRequestProperty("accept","application/json");int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();StringBuilder sb=new StringBuilder();if(in!=null)try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}if(code<200||code>=300)throw new IOException("TMDb HTTP "+code);return new JSONObject(sb.toString());}
}
