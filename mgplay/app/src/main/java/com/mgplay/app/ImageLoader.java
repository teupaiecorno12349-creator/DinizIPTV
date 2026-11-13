package com.mgplay.app;

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
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private ImageLoader() {}

    public static void load(ImageView target, String url) {
        target.setTag(url);
        if (url == null || url.trim().isEmpty()) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) { target.setImageBitmap(cached); return; }
        IO.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "MGPlay/1.0");
                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
                input.close();
                if (bitmap != null) CACHE.put(url, bitmap);
            } catch (Exception ignored) {
            } finally { if (connection != null) connection.disconnect(); }
            Bitmap result = bitmap;
            if (result != null) MAIN.post(() -> { if (url.equals(target.getTag())) target.setImageBitmap(result); });
        });
    }
}