package com.rsspro.data;

public class Feed {
    public String url;
    public String title;
    public long lastFetch;
    public String lastError;
    public String imageUrl;
    public boolean initialized;

    public Feed() {
    }

    public Feed(String url, String title) {
        this.url = url;
        this.title = title;
    }

    public String displayTitle() {
        if (title != null && title.trim().length() > 0) {
            return title.trim();
        }
        if (url == null) {
            return "Fuente";
        }
        String value = url;
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
