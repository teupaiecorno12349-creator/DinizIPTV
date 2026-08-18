package com.diniz.iptv.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class Auth {
    private Auth(){}
    private static byte[] secret(Context c){SharedPreferences p=c.getSharedPreferences("server",Context.MODE_PRIVATE);String s=p.getString("secret",null);if(s==null){byte[] b=new byte[32];new SecureRandom().nextBytes(b);s=Base64.encodeToString(b,Base64.NO_WRAP);p.edit().putString("secret",s).apply();}return Base64.decode(s,Base64.NO_WRAP);}
    public static String issue(Context c,String username,long accountExpiry) throws Exception {String payload=username+"|"+accountExpiry+"|"+System.currentTimeMillis();String p64=Base64.encodeToString(payload.getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);return p64+"."+sign(c,p64);}
    public static String validate(Context c,String token) throws Exception {if(token==null)return null;String[] parts=token.split("\\.");if(parts.length!=2)return null;String expected=sign(c,parts[0]);if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),parts[1].getBytes(StandardCharsets.UTF_8)))return null;String payload=new String(Base64.decode(parts[0],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),StandardCharsets.UTF_8);String[] p=payload.split("\\|");if(p.length<3)return null;long exp=Long.parseLong(p[1]);if(System.currentTimeMillis()>exp)return null;return p[0];}
    private static String sign(Context c,String data) throws Exception {Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret(c),"HmacSHA256"));return Base64.encodeToString(m.doFinal(data.getBytes(StandardCharsets.UTF_8)),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
}
