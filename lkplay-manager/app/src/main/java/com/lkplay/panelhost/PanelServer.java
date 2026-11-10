package com.lkplay.panelhost;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import fi.iki.elonen.NanoHTTPD;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PanelServer extends NanoHTTPD {
    private static final String ALIAS="lkplay_local_lists_v1";
    private final Context ctx;
    private final Map<String,Long> tokens=new ConcurrentHashMap<>();
    public PanelServer(Context c){super("0.0.0.0",8765);ctx=c.getApplicationContext();}
    private SharedPreferences prefs(){return ctx.getSharedPreferences("lkplay_host",Context.MODE_PRIVATE);}
    public static String localIp(){
        try{Enumeration<NetworkInterface> ns=NetworkInterface.getNetworkInterfaces();while(ns.hasMoreElements()){NetworkInterface n=ns.nextElement();if(!n.isUp()||n.isLoopback())continue;Enumeration<InetAddress> as=n.getInetAddresses();while(as.hasMoreElements()){InetAddress a=as.nextElement();if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&!a.isLinkLocalAddress())return a.getHostAddress();}}}catch(Exception ignored){}return "127.0.0.1";
    }
    public static String sha256(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format(Locale.ROOT,"%02x",v&255));return x.toString();}catch(Exception e){throw new RuntimeException(e);}}
    private JSONObject body(IHTTPSession s)throws Exception{Map<String,String> files=new HashMap<>();s.parseBody(files);String raw=files.get("postData");return raw==null||raw.isEmpty()?new JSONObject():new JSONObject(raw);}
    private String bearer(IHTTPSession s){String a=s.getHeaders().get("authorization");return a!=null&&a.toLowerCase(Locale.ROOT).startsWith("bearer ")?a.substring(7).trim():"";}
    private boolean valid(String t){Long until=tokens.get(t);if(until==null)return false;if(until<System.currentTimeMillis()){tokens.remove(t);return false;}return true;}
    private Response json(Response.IStatus status,JSONObject o){Response r=newFixedLengthResponse(status,"application/json; charset=utf-8",o.toString());r.addHeader("Cache-Control","no-store");r.addHeader("X-Content-Type-Options","nosniff");return r;}
    private JSONObject err(String s)throws Exception{return new JSONObject().put("ok",false).put("error",s);}
    @Override public Response serve(IHTTPSession s){
        try{
            String path=s.getUri();Method method=s.getMethod();
            if(method==Method.GET&&(path.equals("/")||path.equals("/index.html"))){InputStream in=ctx.getAssets().open("panel.html");byte[] b=read(in);return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8",new ByteArrayInputStream(b),b.length);}
            if(method==Method.GET&&path.equals("/health"))return json(Response.Status.OK,new JSONObject().put("ok",true));
            if(method==Method.POST&&(path.equals("/api/panel/login")||path.equals("/api/device/login"))){
                JSONObject b=body(s);String m=b.optString("mac","").trim().toUpperCase(Locale.ROOT).replace('-',':');
                String k=b.optString(path.contains("/device/")?"device_key":"deviceKey",b.optString("key",""));
                String savedMac=prefs().getString("mac",""),hash=prefs().getString("key_hash","");
                if(savedMac.isEmpty()||hash.isEmpty())return json(Response.Status.FORBIDDEN,err("Configure MAC e chave na tela do aplicativo."));
                if(!savedMac.equals(m)||!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),sha256(k).getBytes(StandardCharsets.UTF_8)))return json(Response.Status.UNAUTHORIZED,err("MAC ou chave incorretos."));
                byte[] random=new byte[32];new SecureRandom().nextBytes(random);String token=Base64.encodeToString(random,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);tokens.put(token,System.currentTimeMillis()+43200000L);
                JSONObject device=new JSONObject().put("mac",savedMac).put("name","LK Play");
                return json(Response.Status.OK,new JSONObject().put("ok",true).put("access_token",token).put("token_type","Bearer").put("expires_in",43200).put("device",device));
            }
            String token=bearer(s);if(!valid(token))return json(Response.Status.UNAUTHORIZED,err("Sessão inválida. Entre novamente."));
            if(method==Method.GET&&(path.equals("/api/panel/playlists")||path.equals("/api/device/playlists"))){JSONObject o=new JSONObject().put("ok",true).put("playlists",getLists());return json(Response.Status.OK,o);}
            if(method==Method.POST&&path.equals("/api/panel/playlists")){
                JSONObject item=make(body(s),null);JSONArray a=getLists();item.put("id",UUID.randomUUID().toString());a.put(item);saveLists(a);return json(Response.Status.CREATED,new JSONObject().put("ok",true).put("playlist",item));
            }
            if(path.startsWith("/api/panel/playlists/")){
                String id=path.substring("/api/panel/playlists/".length());JSONArray old=getLists(),next=new JSONArray();JSONObject result=null;JSONObject b=method==Method.DELETE?new JSONObject():body(s);
                for(int i=0;i<old.length();i++){
                    JSONObject p=old.getJSONObject(i);
                    if(p.optString("id").equals(id)){
                        if(method==Method.DELETE){result=p;continue;}
                        if(method!=Method.PATCH&&method!=Method.PUT)return json(Response.Status.METHOD_NOT_ALLOWED,err("Método não permitido."));
                        result=make(b,p);result.put("id",id);next.put(result);
                    } else next.put(p);
                }
                if(result==null)return json(Response.Status.NOT_FOUND,err("Lista não encontrada."));
                saveLists(next);
                if(method==Method.DELETE)return json(Response.Status.OK,new JSONObject().put("ok",true));
                return json(Response.Status.OK,new JSONObject().put("ok",true).put("playlist",result));
            }
            return json(Response.Status.NOT_FOUND,err("Rota não encontrada."));
        }catch(Exception e){try{return json(Response.Status.BAD_REQUEST,err(e.getMessage()==null?"Requisição inválida.":e.getMessage()));}catch(Exception ignored){return newFixedLengthResponse("Erro");}}
    }
    private JSONObject make(JSONObject b,JSONObject old)throws Exception{
        String type=b.optString("type",old==null?"":old.optString("type")).toLowerCase(Locale.ROOT),name=b.optString("name",old==null?"":old.optString("name")).trim();
        if(name.isEmpty())throw new IllegalArgumentException("Informe o nome da lista.");
        if(!type.equals("m3u")&&!type.equals("xtream"))throw new IllegalArgumentException("Tipo de lista inválido.");
        JSONObject p=new JSONObject().put("name",name).put("type",type);
        if(type.equals("m3u")){String u=b.optString("m3u_url",old==null?"":old.optString("m3u_url"));checkUrl(u);p.put("m3u_url",u).put("server_url",JSONObject.NULL).put("username",JSONObject.NULL).put("password",JSONObject.NULL);}
        else{String u=b.optString("server_url",old==null?"":old.optString("server_url")),user=b.optString("username",old==null?"":old.optString("username")),pass=b.optString("password",old==null?"":old.optString("password"));checkUrl(u);if(user.trim().isEmpty()||pass.trim().isEmpty())throw new IllegalArgumentException("Informe usuário e senha Xtream.");p.put("server_url",u).put("username",user).put("password",pass).put("m3u_url",JSONObject.NULL);}
        return p;
    }
    private void checkUrl(String value)throws Exception{URI u=new URI(value.trim());if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null)throw new IllegalArgumentException("URL inválida.");}
    private JSONArray getLists(){String x=prefs().getString("lists_enc","");if(x.isEmpty())return new JSONArray();try{return new JSONArray(decrypt(x));}catch(Exception e){return new JSONArray();}}
    private void saveLists(JSONArray a)throws Exception{prefs().edit().putString("lists_enc",encrypt(a.toString())).apply();}
    private SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (SecretKey)ks.getKey(ALIAS,null);}
    private String encrypt(String s)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] iv=c.getIV(),data=c.doFinal(s.getBytes(StandardCharsets.UTF_8)),all=new byte[1+iv.length+data.length];all[0]=(byte)iv.length;System.arraycopy(iv,0,all,1,iv.length);System.arraycopy(data,0,all,1+iv.length,data.length);return Base64.encodeToString(all,Base64.NO_WRAP);}
    private String decrypt(String s)throws Exception{byte[] a=Base64.decode(s,Base64.DEFAULT);int n=a[0]&255;byte[] iv=Arrays.copyOfRange(a,1,1+n),data=Arrays.copyOfRange(a,1+n,a.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));return new String(c.doFinal(data),StandardCharsets.UTF_8);}
    private byte[] read(InputStream in)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}
}
