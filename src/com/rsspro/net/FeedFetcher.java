package com.rsspro.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public final class FeedFetcher {
    private static final int MAX_BYTES = 6 * 1024 * 1024;
    private static final int MAX_PAGE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "RSSPro/1.3.1 (Android RSS reader)";
    private static final String FEED_ACCEPT =
            "application/rss+xml, application/atom+xml, application/xml, text/xml, */*";
    private static final String PAGE_ACCEPT =
            "text/html, application/xhtml+xml, text/plain;q=0.8, */*;q=0.5";

    private FeedFetcher() {
    }

    public static FetchResult fetch(String rawUrl) throws IOException {
        return fetchInternal(rawUrl, FEED_ACCEPT, MAX_BYTES, "6 MB");
    }

    public static FetchResult fetchPage(String rawUrl) throws IOException {
        return fetchInternal(rawUrl, PAGE_ACCEPT, MAX_PAGE_BYTES, "3 MB");
    }

    private static FetchResult fetchInternal(String rawUrl, String accept, int limit,
                                             String limitLabel) throws IOException {
        String current = normalizeUrl(rawUrl);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = open(current, accept);
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().length() == 0) {
                    throw new IOException("Redirección sin destino.");
                }
                current = normalizeUrl(new URL(new URL(current), location).toString());
                continue;
            }
            if (code < 200 || code >= 300) {
                String message = connection.getResponseMessage();
                connection.disconnect();
                throw new IOException("HTTP " + code + (message == null ? "" : " " + message));
            }

            String contentType = connection.getContentType();
            InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }
            byte[] body = readLimited(stream, limit, limitLabel);
            connection.disconnect();
            return new FetchResult(body, current, contentType == null ? "" : contentType);
        }
        throw new IOException("Demasiadas redirecciones.");
    }

    public static String normalizeUrl(String raw) throws IOException {
        if (raw == null) {
            throw new IOException("La URL está vacía.");
        }
        String value = raw.trim();
        if (value.length() == 0) {
            throw new IOException("La URL está vacía.");
        }
        if (value.indexOf("://") < 0) {
            value = "https://" + value;
        }
        URL url;
        try {
            url = new URL(value);
        } catch (Exception ex) {
            throw new IOException("La URL no es válida.");
        }
        String protocol = url.getProtocol().toLowerCase(Locale.US);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IOException("Solo se admiten URLs HTTP o HTTPS.");
        }
        if (url.getHost() == null || url.getHost().trim().length() == 0) {
            throw new IOException("La URL no contiene un servidor.");
        }
        return url.toString();
    }

    private static HttpURLConnection open(String url, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Accept-Encoding", "gzip");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setUseCaches(false);
        return connection;
    }

    private static byte[] readLimited(InputStream input, int limit, String limitLabel)
            throws IOException {
        BufferedInputStream stream = new BufferedInputStream(input);
        ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        byte[] buffer = new byte[16384];
        int read;
        int total = 0;
        try {
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IOException("La respuesta supera el límite de " + limitLabel + ".");
                }
                out.write(buffer, 0, read);
            }
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
        return out.toByteArray();
    }
}
