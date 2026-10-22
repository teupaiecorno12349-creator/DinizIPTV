package com.diniz.iptv.player;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.InetAddress;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

/**
 * Per-app network configuration. DNS choices apply to requests made by this app,
 * never to Android's global resolver or other installed applications.
 */
public final class NetworkStack {
    public static final String PREFS = "marcin_settings";
    public static final String DNS_MODE = "dns_mode";
    public static final String RESOLUTION = "resolution";
    public static final String BUFFER_MODE = "buffer_mode";
    public static final String AUTOPLAY_NEXT = "autoplay_next";

    private static volatile String mode = "system";
    private static volatile OkHttpClient client;
    private NetworkStack() {}

    public static synchronized void apply(Context context, String newMode) {
        mode = (newMode == null || newMode.trim().isEmpty()) ? "system" : newMode;
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(DNS_MODE, mode).apply();
        client = null;
    }

    public static String mode(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(DNS_MODE, "system");
    }

    public static synchronized OkHttpClient client(Context context) {
        String requested = mode(context);
        if (client != null && requested.equals(mode)) return client;
        mode = requested;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(7))
                .readTimeout(java.time.Duration.ofSeconds(12))
                .writeTimeout(java.time.Duration.ofSeconds(12))
                .followRedirects(true)
                .followSslRedirects(true);
        if (!"system".equals(mode)) {
            try {
                OkHttpClient bootstrap = new OkHttpClient.Builder()
                        .connectTimeout(java.time.Duration.ofSeconds(8))
                        .readTimeout(java.time.Duration.ofSeconds(10))
                        .build();
                DnsOverHttps resolver;
                if ("google".equals(mode)) {
                    resolver = new DnsOverHttps.Builder()
                            .client(bootstrap)
                            .url(HttpUrl.get("https://dns.google/dns-query"))
                            .bootstrapDnsHosts(
                                    InetAddress.getByName("8.8.8.8"),
                                    InetAddress.getByName("8.8.4.4"))
                            .build();
                } else {
                    resolver = new DnsOverHttps.Builder()
                            .client(bootstrap)
                            .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                            .bootstrapDnsHosts(
                                    InetAddress.getByName("1.1.1.1"),
                                    InetAddress.getByName("1.0.0.1"))
                            .build();
                }
                builder.dns(resolver);
            } catch (Exception ignored) {
                // A failed DoH setup falls back to Android DNS rather than breaking playback.
            }
        }
        client = builder.build();
        return client;
    }
}
