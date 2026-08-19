package com.diniz.iptv.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<>(32);
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ImageLoader() {}

    public static void load(ImageView view, String url) {
        if (url == null || url.trim().isEmpty()) return;
        String clean = url.trim();
        view.setTag(clean);

        Bitmap memory = MEMORY.get(clean);
        if (memory != null) {
            view.setImageBitmap(memory);
            return;
        }

        IO.execute(() -> {
            try {
                File dir = new File(view.getContext().getCacheDir(), "posters");
                if (!dir.exists()) dir.mkdirs();
                File cachedFile = new File(dir, key(clean) + ".img");

                Bitmap bitmap = null;
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    bitmap = BitmapFactory.decodeFile(cachedFile.getAbsolutePath());
                }

                if (bitmap == null) {
                    HttpURLConnection c = (HttpURLConnection) new URL(clean).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "DinizIPTV-Mobile/1.3.1");
                    try (InputStream in = c.getInputStream()) {
                        bitmap = BitmapFactory.decodeStream(in);
                    } finally {
                        c.disconnect();
                    }

                    if (bitmap != null) {
                        try (FileOutputStream out = new FileOutputStream(cachedFile)) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
                        } catch (Exception ignored) {}
                    }
                }

                if (bitmap != null) {
                    final Bitmap result = bitmap;
                    MEMORY.put(clean, result);
                    MAIN.post(() -> {
                        if (clean.equals(view.getTag())) view.setImageBitmap(result);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private static String key(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
