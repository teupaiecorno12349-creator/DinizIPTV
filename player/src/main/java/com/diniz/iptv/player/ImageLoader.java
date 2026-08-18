package com.diniz.iptv.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(24);
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ImageLoader() {}

    public static void load(ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        view.setTag(url);
        Bitmap cached = CACHE.get(url);
        if (cached != null) { view.setImageBitmap(cached); return; }
        IO.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(true);
                try (InputStream in = c.getInputStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) {
                        CACHE.put(url, b);
                        MAIN.post(() -> { if (url.equals(view.getTag())) view.setImageBitmap(b); });
                    }
                }
            } catch (Exception ignored) {}
        });
    }
}
