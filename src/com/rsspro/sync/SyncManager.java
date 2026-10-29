package com.rsspro.sync;

import android.content.Context;
import android.util.Log;

import com.rsspro.core.RetentionPolicy;
import com.rsspro.data.Article;
import com.rsspro.data.Feed;
import com.rsspro.data.ParsedFeed;
import com.rsspro.net.FeedFetcher;
import com.rsspro.net.FeedParser;
import com.rsspro.net.FetchResult;
import com.rsspro.store.Store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class SyncManager {
    private static final String TAG = "RSSProSync";
    private static final int MAX_ARTICLES_PER_FEED = 200;
    private static final int MAX_ARTICLES_TOTAL = 4000;

    public static final class Result {
        public final List<Article> newArticles = new ArrayList<Article>();
        public final List<String> errors = new ArrayList<String>();
        public int successfulFeeds;
        public int attemptedFeeds;

        public int errorCount() {
            return errors.size();
        }
    }

    private static final class Outcome {
        final Feed feed;
        ParsedFeed parsed;
        String error;

        Outcome(Feed feed) {
            this.feed = feed;
        }
    }

    private SyncManager() {
    }

    public static Result refreshAll(Context context) {
        synchronized (Store.SYNC_LOCK) {
            return refreshLocked(context);
        }
    }

    public static int pruneCached(Context context) {
        synchronized (Store.SYNC_LOCK) {
            Store store = new Store(context);
            return store.pruneOldArticles(store.getRetentionDays(),
                    System.currentTimeMillis());
        }
    }

    private static Result refreshLocked(Context context) {
        Store store = new Store(context);
        Result result = new Result();
        int retentionDays = store.getRetentionDays();
        store.pruneOldArticles(retentionDays, System.currentTimeMillis());
        List<Feed> feeds = store.getFeeds();
        result.attemptedFeeds = feeds.size();
        if (feeds.isEmpty()) {
            return result;
        }

        int threads = Math.max(1, Math.min(4, feeds.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Outcome>> tasks = new ArrayList<Callable<Outcome>>();
        for (final Feed feed : feeds) {
            tasks.add(new Callable<Outcome>() {
                @Override
                public Outcome call() {
                    Outcome outcome = new Outcome(feed);
                    try {
                        FetchResult fetched = FeedFetcher.fetch(feed.url);
                        outcome.parsed = FeedParser.parse(fetched.body, fetched.finalUrl);
                    } catch (Exception ex) {
                        outcome.error = message(ex);
                    }
                    return outcome;
                }
            });
        }

        List<Outcome> outcomes = new ArrayList<Outcome>();
        try {
            List<Future<Outcome>> futures = executor.invokeAll(tasks, 180, TimeUnit.SECONDS);
            for (int i = 0; i < futures.size(); i++) {
                Future<Outcome> future = futures.get(i);
                try {
                    outcomes.add(future.get());
                } catch (Exception ex) {
                    Outcome failed = new Outcome(feeds.get(i));
                    failed.error = message(ex);
                    outcomes.add(failed);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            for (Feed feed : feeds) {
                Outcome failed = new Outcome(feed);
                failed.error = "Sincronización interrumpida.";
                outcomes.add(failed);
            }
        } finally {
            executor.shutdownNow();
        }

        long now = System.currentTimeMillis();
        List<Article> allArticles = store.getArticles();
        Map<String, Article> articleMap = new LinkedHashMap<String, Article>();
        for (Article article : allArticles) {
            articleMap.put(article.id, article);
        }
        Set<String> cachedIds = new HashSet<String>(articleMap.keySet());
        Set<String> seen = store.getSeenIds();
        Set<String> newSeen = new HashSet<String>();

        for (Outcome outcome : outcomes) {
            Feed feed = outcome.feed;
            feed.lastFetch = now;
            if (outcome.error != null) {
                feed.lastError = outcome.error;
                result.errors.add(feed.displayTitle() + ": " + outcome.error);
                continue;
            }
            result.successfulFeeds++;
            feed.lastError = "";
            boolean firstFetch = !feed.initialized;
            if ((feed.title == null || feed.title.trim().length() == 0)
                    && outcome.parsed.title != null
                    && outcome.parsed.title.trim().length() > 0) {
                feed.title = outcome.parsed.title.trim();
            }
            feed.initialized = true;
            if (outcome.parsed.imageUrl != null && outcome.parsed.imageUrl.length() > 0) {
                feed.imageUrl = outcome.parsed.imageUrl;
            }
            String feedTitle = feed.displayTitle();

            for (Article article : outcome.parsed.articles) {
                article.feedTitle = feedTitle;
                boolean expired = RetentionPolicy.isExpired(article, now, retentionDays);
                boolean known = seen.contains(article.id)
                        || cachedIds.contains(article.id)
                        || newSeen.contains(article.id);
                if (!expired && !firstFetch && !known) {
                    result.newArticles.add(article);
                }
                seen.add(article.id);
                newSeen.add(article.id);
                if (!expired) {
                    Article cached = articleMap.get(article.id);
                    if (cached != null) {
                        article.fullContent = cached.fullContent;
                        article.fullFetchAt = cached.fullFetchAt;
                        if (article.category == null || article.category.length() == 0) {
                            article.category = cached.category;
                        }
                    }
                    articleMap.put(article.id, article);
                }
            }
            for (Article cached : articleMap.values()) {
                if (feed.url.equals(cached.feedId)) {
                    cached.feedTitle = feedTitle;
                }
            }
        }

        List<Article> merged = new ArrayList<Article>(articleMap.values());
        Collections.sort(merged, new Comparator<Article>() {
            @Override
            public int compare(Article left, Article right) {
                if (left.publishedAt == right.publishedAt) {
                    return 0;
                }
                return right.publishedAt > left.publishedAt ? 1 : -1;
            }
        });
        merged = limitArticles(merged);

        Collections.sort(result.newArticles, new Comparator<Article>() {
            @Override
            public int compare(Article left, Article right) {
                if (left.publishedAt == right.publishedAt) {
                    return 0;
                }
                return right.publishedAt > left.publishedAt ? 1 : -1;
            }
        });

        seen.addAll(newSeen);
        store.saveFeeds(feeds);
        store.saveArticles(merged);
        store.addSeenIds(newSeen);
        store.setLastSync(now);
        Log.i(TAG, "Sincronización: " + result.successfulFeeds + "/" + feeds.size()
                + " fuentes, " + result.newArticles.size() + " nuevos");
        return result;
    }

    private static List<Article> limitArticles(List<Article> articles) {
        Map<String, Integer> perFeed = new HashMap<String, Integer>();
        List<Article> kept = new ArrayList<Article>();
        for (Article article : articles) {
            if (kept.size() >= MAX_ARTICLES_TOTAL) {
                break;
            }
            Integer count = perFeed.get(article.feedId);
            int value = count == null ? 0 : count;
            if (value >= MAX_ARTICLES_PER_FEED) {
                continue;
            }
            perFeed.put(article.feedId, value + 1);
            kept.add(article);
        }
        return kept;
    }

    private static String message(Throwable throwable) {
        String value = throwable.getMessage();
        if (value == null || value.trim().length() == 0) {
            value = throwable.getClass().getSimpleName();
        }
        if (value.length() > 240) {
            value = value.substring(0, 240);
        }
        return value;
    }
}
