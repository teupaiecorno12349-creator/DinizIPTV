package com.diniz.iptv.admin;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TeraBoxClient {
    public static final class CloudFile {
        public final String path;
        public final String name;
        public final long fsId;
        public final long size;
        public CloudFile(String path,String name,long fsId,long size){this.path=path;this.name=name;this.fsId=fsId;this.size=size;}
    }

    private final TeraBoxStore store;

    public TeraBoxClient(Context context){ store=new TeraBoxStore(context); }

    public String authorizationUrl() throws Exception {
        if(store.clientId().isEmpty()) throw new Exception("Informe o client_id do TeraBox.");
        return "https://www.terabox.com/wap/outside/login?clientId=" + enc(store.clientId()) + "&isFromApp=1";
    }

    public synchronized void exchangeAuthorizationCode(String code) throws Exception {
        if(!store.configured()) throw new Exception("Configure client_id, client_secret e private_secret primeiro.");
        long ts=System.currentTimeMillis()/1000L;
        Map<String,String> f=new HashMap<>();
        f.put("client_id",store.clientId());
        f.put("client_secret",store.clientSecret());
        f.put("grant_type","authorization_code");
        f.put("code",code);
        f.put("timestamp",String.valueOf(ts));
        f.put("sign",sign(ts));
        JSONObject r=new JSONObject(postMultipart("https://www.terabox.com/oauth/gettoken",f));
        ensureZero(r,"Falha ao autorizar TeraBox");
        saveTokenData(r.getJSONObject("data"));
        updateTokenInfo();
    }

    public synchronized String ensureAccessToken() throws Exception {
        String token=store.accessToken();
        if(!token.isEmpty() && store.accessExpiry()>System.currentTimeMillis()+120000L) return token;
        if(store.refreshToken().isEmpty()) throw new Exception("Conta TeraBox ainda não autorizada.");
        refresh();
        return store.accessToken();
    }

    public synchronized void refresh() throws Exception {
        if(!store.configured()) throw new Exception("Credenciais TeraBox incompletas.");
        String rt=store.refreshToken();
        if(rt.isEmpty()) throw new Exception("Refresh token TeraBox ausente. Autorize novamente.");
        long ts=System.currentTimeMillis()/1000L;
        Map<String,String> f=new HashMap<>();
        f.put("client_id",store.clientId());
        f.put("client_secret",store.clientSecret());
        f.put("refresh_token",rt);
        f.put("timestamp",String.valueOf(ts));
        f.put("sign",sign(ts));
        JSONObject r=new JSONObject(postMultipart("https://www.terabox.com/oauth/refreshtoken",f));
        ensureZero(r,"Falha ao renovar TeraBox");
        saveTokenData(r.getJSONObject("data"));
        updateTokenInfo();
    }

    private void saveTokenData(JSONObject d){
        store.setAccessToken(d.optString("access_token",""));
        store.setRefreshToken(d.optString("refresh_token",""));
        long expires=d.optLong("expires_in",172800L);
        store.setAccessExpiry(System.currentTimeMillis()+Math.max(60L,expires-60L)*1000L);
    }

    private void updateTokenInfo() throws Exception {
        Map<String,String> f=new HashMap<>();f.put("access_token",store.accessToken());
        JSONObject r=new JSONObject(postMultipart("https://www.terabox.com/oauth/tokeninfo",f));
        ensureZero(r,"Falha ao consultar token TeraBox");
        JSONObject d=r.optJSONObject("data");
        if(d!=null){String domain=d.optString("api_domain","");if(!domain.isEmpty())store.setApiDomain(domain);}
    }

    public List<CloudFile> listVideosRecursive(String rootDir) throws Exception {
        ensureAccessToken();
        String root=normalizeDir(rootDir);
        ArrayDeque<String> dirs=new ArrayDeque<>();
        Set<String> seen=new HashSet<>();
        List<CloudFile> out=new ArrayList<>();
        dirs.add(root);
        int dirCount=0;
        while(!dirs.isEmpty()){
            String dir=dirs.removeFirst();
            if(!seen.add(dir))continue;
            if(++dirCount>2000)throw new Exception("Muitas pastas no TeraBox. Escolha uma pasta raiz menor.");
            for(int page=1;page<=100;page++){
                JSONObject r=listPage(dir,page,500);
                JSONArray info=r.optJSONArray("info");
                if(info==null||info.length()==0)break;
                for(int i=0;i<info.length();i++){
                    JSONObject o=info.optJSONObject(i);if(o==null)continue;
                    String name=o.optString("server_filename",o.optString("filename",""));
                    String path=o.optString("path",join(dir,name));
                    int isdir=o.optInt("isdir",0);
                    if(isdir==1){dirs.add(path);continue;}
                    int cat=o.optInt("category",0);
                    if(cat==1||looksVideo(name)){
                        out.add(new CloudFile(path,name,o.optLong("fs_id",0L),o.optLong("size",0L)));
                        if(out.size()>20000)throw new Exception("Mais de 20.000 vídeos encontrados. Sincronize uma pasta menor.");
                    }
                }
                if(info.length()<500)break;
            }
        }
        return out;
    }

    private JSONObject listPage(String dir,int page,int num) throws Exception {
        String token=ensureAccessToken();
        String u="https://"+cleanHost(store.apiDomain())+"/openapi/api/list?access_tokens="+enc(token)+"&dir="+enc(dir)+"&num="+num+"&page="+page+"&order=name&desc=0&web=1";
        String body=getText(u,"application/json");
        JSONObject r=new JSONObject(body);
        int errno=r.optInt("errno",0);
        if(errno==200003||errno==200002){refresh();return listPage(dir,page,num);}
        if(errno!=0)throw new Exception("TeraBox list errno="+errno+" "+r.optString("show_msg",""));
        return r;
    }

    public String streamingPlaylist(String path,String quality) throws Exception {
        String token=ensureAccessToken();
        String q=quality;
        if(!"M3U8_AUTO_480".equals(q)&&!"M3U8_AUTO_720".equals(q)&&!"M3U8_AUTO_1080".equals(q))q="M3U8_AUTO_720";
        String u="https://"+cleanHost(store.apiDomain())+"/openapi/api/streaming?access_tokens="+enc(token)+"&path="+enc(path)+"&type="+enc(q);
        String body=getText(u,"application/vnd.apple.mpegurl");
        String t=body.trim();
        if(t.startsWith("{")){
            JSONObject r=new JSONObject(t);int errno=r.optInt("errno",-1);
            if(errno==200003||errno==200002){refresh();return streamingPlaylist(path,q);}
            throw new Exception("TeraBox streaming errno="+errno+" "+r.optString("show_msg",""));
        }
        if(!t.startsWith("#EXTM3U"))throw new Exception("O TeraBox não retornou um playlist M3U8 válido.");
        return body;
    }

    public TeraBoxStore store(){return store;}

    private String sign(long ts) throws Exception {
        String s=store.clientId()+"_"+ts+"_"+store.clientSecret()+"_"+store.privateSecret();
        MessageDigest md=MessageDigest.getInstance("MD5");
        byte[] b=md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format(Locale.US,"%02x",v&0xff));return x.toString();
    }

    private static String postMultipart(String url,Map<String,String> fields) throws Exception {
        String boundary="----DinizIPTV"+System.currentTimeMillis();
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        try(OutputStream out=c.getOutputStream()){
            for(Map.Entry<String,String> e:fields.entrySet()){
                String p="--"+boundary+"\r\nContent-Disposition: form-data; name=\""+e.getKey()+"\"\r\n\r\n"+(e.getValue()==null?"":e.getValue())+"\r\n";
                out.write(p.getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(c);
    }

    private static String getText(String url,String accept) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);c.setRequestProperty("Accept",accept);
        return readResponse(c);
    }

    private static String readResponse(HttpURLConnection c) throws Exception {
        int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        if(in==null)throw new Exception("HTTP "+code);
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);in.close();
        String s=b.toString(StandardCharsets.UTF_8.name());if(code<200||code>=300)throw new Exception("HTTP "+code+": "+s);return s;
    }

    private static void ensureZero(JSONObject r,String prefix) throws Exception {int errno=r.optInt("errno",0);if(errno!=0)throw new Exception(prefix+" (errno "+errno+"): "+r.optString("show_msg",r.optString("errmsg","")));}
    private static String enc(String s)throws Exception{return URLEncoder.encode(s==null?"":s,"UTF-8");}
    private static String cleanHost(String h){String x=h==null?"":h.trim();x=x.replaceFirst("^https?://","");while(x.endsWith("/"))x=x.substring(0,x.length()-1);return x.isEmpty()?"www.terabox.com":x;}
    private static String normalizeDir(String d){String x=d==null?"/":d.trim();if(x.isEmpty())x="/";if(!x.startsWith("/"))x="/"+x;while(x.length()>1&&x.endsWith("/"))x=x.substring(0,x.length()-1);return x;}
    private static String join(String dir,String name){if("/".equals(dir))return "/"+name;return dir+"/"+name;}
    private static boolean looksVideo(String n){String x=n.toLowerCase(Locale.ROOT);return x.endsWith(".mp4")||x.endsWith(".mkv")||x.endsWith(".avi")||x.endsWith(".mov")||x.endsWith(".m4v")||x.endsWith(".ts")||x.endsWith(".webm");}
}
