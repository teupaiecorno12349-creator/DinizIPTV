package com.mgplay.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

public final class DeviceIdentity {
    private static final String PREFS = "mgplay_identity";
    private DeviceIdentity() {}

    public static synchronized void ensure(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getString("mac", "").isEmpty() && !p.getString("device_key", "").isEmpty()) return;
        byte[] mac = new byte[6];
        byte[] key = new byte[24];
        SecureRandom random = new SecureRandom();
        random.nextBytes(mac);
        random.nextBytes(key);
        mac[0] = (byte) ((mac[0] & 0xFC) | 0x02);
        StringBuilder macText = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) macText.append(':');
            macText.append(String.format(Locale.ROOT, "%02X", mac[i] & 0xff));
        }
        p.edit().putString("mac", macText.toString())
            .putString("device_key", hex(key))
            .putString("device_key_hash", sha256(hex(key)))
            .apply();
    }

    public static String mac(Context c) { ensure(c); return prefs(c).getString("mac", ""); }
    public static String key(Context c) { ensure(c); return prefs(c).getString("device_key", ""); }
    public static String keyHash(Context c) { ensure(c); return prefs(c).getString("device_key_hash", ""); }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return hex(digest);
        } catch (Exception e) { throw new IllegalStateException("SHA-256 indisponível", e); }
    }

    public static boolean matches(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return s.toString();
    }
}