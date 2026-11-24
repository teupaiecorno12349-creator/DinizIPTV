package com.diniz.iptv.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class TeraBoxStore {
    private static final String PREFS = "terabox_secure";
    private static final String KEY_ALIAS = "diniz_iptv_terabox_v1";
    private final SharedPreferences prefs;

    public TeraBoxStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setClientId(String v){ put("clientId", v); }
    public void setClientSecret(String v){ put("clientSecret", v); }
    public void setPrivateSecret(String v){ put("privateSecret", v); }
    public void setAccessToken(String v){ put("accessToken", v); }
    public void setRefreshToken(String v){ put("refreshToken", v); }
    public void setApiDomain(String v){ put("apiDomain", v); }
    public void setRootDir(String v){ put("rootDir", v); }
    public void setQuality(String v){ put("quality", v); }
    public void setAccessExpiry(long v){ prefs.edit().putLong("accessExpiry", v).apply(); }

    public String clientId(){ return get("clientId"); }
    public String clientSecret(){ return get("clientSecret"); }
    public String privateSecret(){ return get("privateSecret"); }
    public String accessToken(){ return get("accessToken"); }
    public String refreshToken(){ return get("refreshToken"); }
    public String apiDomain(){ String v=get("apiDomain"); return v.isEmpty()?"www.terabox.com":v; }
    public String rootDir(){ String v=get("rootDir"); return v.isEmpty()?"/":v; }
    public String quality(){ String v=get("quality"); return v.isEmpty()?"M3U8_AUTO_720":v; }
    public long accessExpiry(){ return prefs.getLong("accessExpiry", 0L); }

    public boolean configured(){
        return !clientId().isEmpty() && !clientSecret().isEmpty() && !privateSecret().isEmpty();
    }

    public boolean authorized(){
        return !accessToken().isEmpty() && accessExpiry() > System.currentTimeMillis();
    }

    public void clearTokens(){
        setAccessToken(""); setRefreshToken(""); setApiDomain(""); setAccessExpiry(0L);
    }

    private void put(String key, String value){
        try {
            prefs.edit().putString(key, encrypt(value == null ? "" : value)).apply();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao proteger configuração TeraBox", e);
        }
    }

    private String get(String key){
        String enc = prefs.getString(key, "");
        if (enc == null || enc.isEmpty()) return "";
        try { return decrypt(enc); }
        catch (Exception e) { return ""; }
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            kg.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    private String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[1 + iv.length + ct.length];
        out[0] = (byte) iv.length;
        System.arraycopy(iv, 0, out, 1, iv.length);
        System.arraycopy(ct, 0, out, 1 + iv.length, ct.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    private String decrypt(String encoded) throws Exception {
        byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
        int ivLen = all[0] & 0xff;
        byte[] iv = new byte[ivLen];
        byte[] ct = new byte[all.length - 1 - ivLen];
        System.arraycopy(all, 1, iv, 0, ivLen);
        System.arraycopy(all, 1 + ivLen, ct, 0, ct.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    }
}
