package com.rsspro.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final String TAG = "RSSProImage";
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final String USER_AGENT = "RSSPro/1.1 (Android RSS reader)";
    private static final String CACHE_DIR = "images";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(
            (int) Math.min(32L * 1024L * 1024L, Runtime.getRuntime().maxMemory() / 8L)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {
    }

    public static void load(Context context, ImageView view, String rawUrl,
                            int targetWidth, int targetHeight) {
        if (view == null) {
            return;
        }
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.length() == 0) {
            view.setTag(null);
            view.setImageDrawable(null);
            return;
        }
        final Context app = context.getApplicationContext();
        final String key = url + "@" + targetWidth + "x" + targetHeight;
        view.setTag(key);
        Bitmap cached = MEMORY.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = loadBitmap(app, url, targetWidth, targetHeight);
                if (bitmap != null) {
                    MEMORY.put(key, bitmap);
                }
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(view.getTag()) && bitmap != null
                                && !bitmap.isRecycled()) {
                            view.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        });
    }

    public static void clear(ImageView view) {
        if (view == null) {
            return;
        }
        view.setTag(null);
        view.setImageDrawable(null);
    }

    public static void pruneDiskCache(Context context, long maxAgeMillis) {
        File dir = new File(context.getApplicationContext().getCacheDir(), CACHE_DIR);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (File file : files) {
            if (file.isFile() && now - file.lastModified() > maxAgeMillis) {
                if (!file.delete()) {
                    Log.w(TAG, "No se pudo borrar la imagen " + file.getName());
                }
            }
        }
    }

    private static Bitmap loadBitmap(Context context, String url, int targetWidth,
                                     int targetHeight) {
        try {
            File file = diskFile(context, url);
            if (!file.exists()) {
                download(url, file);
            }
            Bitmap bitmap = decode(file, targetWidth, targetHeight);
            if (bitmap == null) {
                file.delete();
            }
            return bitmap;
        } catch (Exception ex) {
            Log.w(TAG, "No se pudo cargar la imagen: " + ex.getMessage());
            return null;
        }
    }

    private static File diskFile(Context context, String url) throws IOException {
        File dir = new File(context.getCacheDir(), CACHE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("No se pudo crear la caché de imágenes.");
        }
        return new File(dir, sha256(url));
    }

    private static void download(String url, File target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "image/*,*/*;q=0.8");
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(true);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            InputStream input = new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream(32768);
            byte[] buffer = new byte[16384];
            int read;
            int total = 0;
            try {
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) {
                        throw new IOException("Imagen demasiado grande.");
                    }
                    out.write(buffer, 0, read);
                }
            } finally {
                input.close();
            }
            File temp = new File(target.getParentFile(), target.getName() + ".tmp");
            FileOutputStream output = new FileOutputStream(temp);
            try {
                output.write(out.toByteArray());
                output.getFD().sync();
            } finally {
                output.close();
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("No se pudo reemplazar la imagen.");
            }
            if (!temp.renameTo(target)) {
                temp.delete();
                throw new IOException("No se pudo guardar la imagen.");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Bitmap decode(File file, int targetWidth, int targetHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int maxWidth = targetWidth > 0 ? targetWidth : 1024;
        int maxHeight = targetHeight > 0 ? targetHeight : 1024;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxWidth
                && bounds.outHeight / (sample * 2) >= maxHeight) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }
        if (bitmap.getWidth() > maxWidth * 2 || bitmap.getHeight() > maxHeight * 2) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, bitmap.getWidth() / 2),
                    Math.max(1, bitmap.getHeight() / 2), true);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            bitmap = scaled;
        }
        return bitmap;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0x0F, 16));
                out.append(Character.forDigit(b & 0x0F, 16));
            }
            return out.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
