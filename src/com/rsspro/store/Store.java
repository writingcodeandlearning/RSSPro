package com.rsspro.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.rsspro.core.RetentionPolicy;
import com.rsspro.data.Article;
import com.rsspro.data.Feed;
import com.rsspro.data.FilterRule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Store {
    public static final String PREFS = "rsspro_prefs";
    public static final Object SYNC_LOCK = new Object();

    private static final String TAG = "RSSProStore";
    private static final String KEY_FEEDS = "feeds";
    private static final String KEY_RULES = "rules";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_INTERVAL = "sync_interval_min";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    private static final String KEY_NOTIF_ASKED = "notif_asked";
    private static final String KEY_SEEN = "seen_ids";
    private static final String KEY_READ = "read_ids";
    private static final String KEY_LAST_SYNC = "last_sync";
    private static final String KEY_RETENTION = "retention_days";
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final String ARTICLES_FILE = "articles.json";
    private static final int MAX_SEEN = 8000;

    private final Context context;
    private final SharedPreferences prefs;

    public Store(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int readThemeMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME, 0);
    }

    public List<Feed> getFeeds() {
        String raw = prefs.getString(KEY_FEEDS, "[]");
        List<Feed> feeds = new ArrayList<Feed>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Feed feed = new Feed();
                feed.url = object.optString("url", "");
                feed.title = object.optString("title", "");
                feed.lastFetch = object.optLong("lastFetch", 0L);
                feed.lastError = object.optString("lastError", "");
                feed.imageUrl = object.optString("imageUrl", "");
                feed.initialized = object.optBoolean("initialized", false);
                if (feed.url.length() > 0) {
                    feeds.add(feed);
                }
            }
        } catch (JSONException ex) {
            Log.e(TAG, "No se pudieron leer las fuentes", ex);
        }
        Collections.sort(feeds, new Comparator<Feed>() {
            @Override
            public int compare(Feed left, Feed right) {
                return left.displayTitle().compareToIgnoreCase(right.displayTitle());
            }
        });
        return feeds;
    }

    public void saveFeeds(List<Feed> feeds) {
        JSONArray array = new JSONArray();
        for (Feed feed : feeds) {
            JSONObject object = new JSONObject();
            try {
                object.put("url", feed.url);
                object.put("title", feed.title == null ? "" : feed.title);
                object.put("lastFetch", feed.lastFetch);
                object.put("lastError", feed.lastError == null ? "" : feed.lastError);
                object.put("imageUrl", feed.imageUrl == null ? "" : feed.imageUrl);
                object.put("initialized", feed.initialized);
                array.put(object);
            } catch (JSONException ex) {
                Log.e(TAG, "No se pudo guardar una fuente", ex);
            }
        }
        prefs.edit().putString(KEY_FEEDS, array.toString()).apply();
    }

    public List<FilterRule> getRules() {
        String raw = prefs.getString(KEY_RULES, "[]");
        List<FilterRule> rules = new ArrayList<FilterRule>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                FilterRule rule = new FilterRule();
                rule.id = object.optString("id", "");
                rule.name = object.optString("name", "");
                rule.pattern = object.optString("pattern", "");
                rule.matchType = enumValue(FilterRule.MatchType.class,
                        object.optString("matchType", ""), FilterRule.MatchType.CONTAINS);
                rule.field = enumValue(FilterRule.Field.class,
                        object.optString("field", ""), FilterRule.Field.ANY);
                rule.caseSensitive = object.optBoolean("caseSensitive", false);
                rule.feedId = object.optString("feedId", "");
                if (rule.feedId.length() == 0) {
                    rule.feedId = null;
                }
                rule.action = enumValue(FilterRule.Action.class,
                        object.optString("action", ""), FilterRule.Action.SHOW);
                rule.enabled = object.optBoolean("enabled", true);
                rules.add(rule);
            }
        } catch (JSONException ex) {
            Log.e(TAG, "No se pudieron leer los filtros", ex);
        }
        return rules;
    }

    public void saveRules(List<FilterRule> rules) {
        JSONArray array = new JSONArray();
        for (FilterRule rule : rules) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", rule.id);
                object.put("name", rule.name == null ? "" : rule.name);
                object.put("pattern", rule.pattern == null ? "" : rule.pattern);
                object.put("matchType", rule.matchType.name());
                object.put("field", rule.field.name());
                object.put("caseSensitive", rule.caseSensitive);
                object.put("feedId", rule.feedId == null ? "" : rule.feedId);
                object.put("action", rule.action.name());
                object.put("enabled", rule.enabled);
                array.put(object);
            } catch (JSONException ex) {
                Log.e(TAG, "No se pudo guardar un filtro", ex);
            }
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply();
    }

    public List<Article> getArticles() {
        File file = new File(context.getFilesDir(), ARTICLES_FILE);
        if (!file.exists()) {
            return new ArrayList<Article>();
        }
        List<Article> articles = new ArrayList<Article>();
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            String raw = new String(out.toByteArray(), StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Article article = new Article();
                article.id = object.optString("id", "");
                article.feedId = object.optString("feedId", "");
                article.feedTitle = object.optString("feedTitle", "");
                article.title = object.optString("title", "");
                article.link = object.optString("link", "");
                article.imageUrl = object.optString("imageUrl", "");
                article.summary = object.optString("summary", "");
                article.content = object.optString("content", "");
                article.fullContent = object.optString("fullContent", "");
                article.author = object.optString("author", "");
                article.category = object.optString("category", "");
                article.publishedAt = object.optLong("publishedAt", 0L);
                article.fullFetchAt = object.optLong("fullFetchAt", 0L);
                if (article.id.length() > 0) {
                    articles.add(article);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "No se pudieron leer los artículos", ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
        return articles;
    }

    public void saveArticles(List<Article> articles) {
        File dir = context.getFilesDir();
        File target = new File(dir, ARTICLES_FILE);
        File temp = new File(dir, ARTICLES_FILE + ".tmp");
        JSONArray array = new JSONArray();
        for (Article article : articles) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", article.id);
                object.put("feedId", article.feedId);
                object.put("feedTitle", article.feedTitle == null ? "" : article.feedTitle);
                object.put("title", article.title == null ? "" : article.title);
                object.put("link", article.link == null ? "" : article.link);
                object.put("imageUrl", article.imageUrl == null ? "" : article.imageUrl);
                object.put("summary", article.summary == null ? "" : article.summary);
                object.put("content", article.content == null ? "" : article.content);
                object.put("fullContent", article.fullContent == null ? "" : article.fullContent);
                object.put("author", article.author == null ? "" : article.author);
                object.put("category", article.category == null ? "" : article.category);
                object.put("publishedAt", article.publishedAt);
                object.put("fullFetchAt", article.fullFetchAt);
                array.put(object);
            } catch (JSONException ex) {
                Log.e(TAG, "No se pudo guardar un artículo", ex);
            }
        }
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temp);
            OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            writer.write(array.toString());
            writer.flush();
            output.getFD().sync();
        } catch (Exception ex) {
            Log.e(TAG, "No se pudo escribir la caché", ex);
            return;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
        }
        if (target.exists() && !target.delete()) {
            Log.w(TAG, "No se pudo reemplazar la caché anterior");
        }
        if (!temp.renameTo(target)) {
            Log.w(TAG, "No se pudo renombrar la caché temporal");
        }
    }

    public Article getArticle(String id) {
        if (id == null) {
            return null;
        }
        for (Article article : getArticles()) {
            if (id.equals(article.id)) {
                return article;
            }
        }
        return null;
    }

    public Set<String> getSeenIds() {
        return new HashSet<String>(prefs.getStringSet(KEY_SEEN, new HashSet<String>()));
    }

    public void addSeenIds(Collection<String> ids) {
        Set<String> seen = getSeenIds();
        seen.addAll(ids);
        if (seen.size() > MAX_SEEN) {
            Iterator<String> iterator = seen.iterator();
            while (seen.size() > MAX_SEEN - 1000 && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        prefs.edit().putStringSet(KEY_SEEN, seen).apply();
    }

    public Set<String> getReadIds() {
        return new HashSet<String>(prefs.getStringSet(KEY_READ, new HashSet<String>()));
    }

    public boolean isRead(String articleId) {
        return articleId != null && prefs.getStringSet(KEY_READ, new HashSet<String>()).contains(articleId);
    }

    public void markRead(String articleId) {
        if (articleId == null) {
            return;
        }
        Set<String> read = getReadIds();
        if (read.add(articleId) && read.size() > 12000) {
            Iterator<String> iterator = read.iterator();
            while (read.size() > 11000 && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        prefs.edit().putStringSet(KEY_READ, read).apply();
    }

    public void markUnread(String articleId) {
        if (articleId == null) {
            return;
        }
        Set<String> read = getReadIds();
        if (read.remove(articleId)) {
            prefs.edit().putStringSet(KEY_READ, read).apply();
        }
    }

    public void markAllRead(Collection<String> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return;
        }
        Set<String> read = getReadIds();
        boolean changed = false;
        for (String id : articleIds) {
            if (id != null && read.add(id)) {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        if (read.size() > 12000) {
            Iterator<String> iterator = read.iterator();
            while (read.size() > 11000 && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        prefs.edit().putStringSet(KEY_READ, read).apply();
    }

    private void removeReadIds(Collection<String> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return;
        }
        Set<String> read = getReadIds();
        if (read.removeAll(articleIds)) {
            prefs.edit().putStringSet(KEY_READ, read).apply();
        }
    }

    public int pruneOldArticles(int retentionDays, long now) {
        synchronized (SYNC_LOCK) {
            if (retentionDays <= 0) {
                return 0;
            }
            List<Article> articles = getArticles();
            List<Article> kept = RetentionPolicy.retain(articles, now, retentionDays);
            if (kept.size() == articles.size()) {
                return 0;
            }
            Set<String> removed = new HashSet<String>();
            for (Article article : articles) {
                if (article.id != null) {
                    removed.add(article.id);
                }
            }
            for (Article article : kept) {
                if (article.id != null) {
                    removed.remove(article.id);
                }
            }
            saveArticles(kept);
            removeReadIds(removed);
            return removed.size();
        }
    }

    public void recordFullContentFetch(String articleId, String fullContent, long fetchedAt) {
        if (articleId == null) {
            return;
        }
        synchronized (SYNC_LOCK) {
            List<Article> articles = getArticles();
            boolean changed = false;
            for (Article article : articles) {
                if (!articleId.equals(article.id)) {
                    continue;
                }
                if (fullContent != null && fullContent.trim().length() > 0) {
                    article.fullContent = fullContent.trim();
                    changed = true;
                }
                article.fullFetchAt = fetchedAt;
                changed = true;
                break;
            }
            if (changed) {
                saveArticles(articles);
            }
        }
    }

    public int getRetentionDays() {
        return prefs.getInt(KEY_RETENTION, DEFAULT_RETENTION_DAYS);
    }

    public void setRetentionDays(int days) {
        if (days < 0) {
            days = 0;
        }
        prefs.edit().putInt(KEY_RETENTION, days).apply();
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME, 0);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME, mode).apply();
    }

    public int getSyncIntervalMinutes() {
        int value = prefs.getInt(KEY_INTERVAL, 30);
        if (value < 15) {
            return 15;
        }
        if (value > 1440) {
            return 1440;
        }
        return value;
    }

    public void setSyncIntervalMinutes(int minutes) {
        prefs.edit().putInt(KEY_INTERVAL, minutes).apply();
    }

    public boolean notificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    public boolean notificationPermissionAsked() {
        return prefs.getBoolean(KEY_NOTIF_ASKED, false);
    }

    public void markNotificationPermissionAsked() {
        prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply();
    }

    public long getLastSync() {
        return prefs.getLong(KEY_LAST_SYNC, 0L);
    }

    public void setLastSync(long value) {
        prefs.edit().putLong(KEY_LAST_SYNC, value).apply();
    }

    public void removeFeed(String feedUrl) {
        synchronized (SYNC_LOCK) {
            List<Feed> feeds = getFeeds();
            for (int i = feeds.size() - 1; i >= 0; i--) {
                if (feeds.get(i).url.equals(feedUrl)) {
                    feeds.remove(i);
                }
            }
            saveFeeds(feeds);

            List<FilterRule> rules = getRules();
            for (int i = rules.size() - 1; i >= 0; i--) {
                if (feedUrl.equals(rules.get(i).feedId)) {
                    rules.remove(i);
                }
            }
            saveRules(rules);

            List<Article> articles = getArticles();
            for (int i = articles.size() - 1; i >= 0; i--) {
                if (feedUrl.equals(articles.get(i).feedId)) {
                    articles.remove(i);
                }
            }
            saveArticles(articles);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value != null) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return fallback;
    }
}
