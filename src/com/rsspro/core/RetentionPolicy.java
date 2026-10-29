package com.rsspro.core;

import com.rsspro.data.Article;

import java.util.ArrayList;
import java.util.List;

public final class RetentionPolicy {
    public static final int NEVER = 0;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private RetentionPolicy() {
    }

    public static long cutoff(long now, int retentionDays) {
        if (retentionDays <= 0) {
            return Long.MIN_VALUE;
        }
        return now - retentionDays * DAY_MILLIS;
    }

    public static boolean isExpired(Article article, long now, int retentionDays) {
        if (article == null || retentionDays <= 0 || article.publishedAt <= 0L) {
            return false;
        }
        return article.publishedAt < cutoff(now, retentionDays);
    }

    public static boolean isExpired(long publishedAt, long now, int retentionDays) {
        if (retentionDays <= 0 || publishedAt <= 0L) {
            return false;
        }
        return publishedAt < cutoff(now, retentionDays);
    }

    public static List<Article> retain(List<Article> articles, long now, int retentionDays) {
        List<Article> kept = new ArrayList<Article>();
        if (articles == null) {
            return kept;
        }
        for (Article article : articles) {
            if (!isExpired(article, now, retentionDays)) {
                kept.add(article);
            }
        }
        return kept;
    }
}
