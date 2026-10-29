package com.rsspro.core;

import com.rsspro.data.Article;

import java.text.Normalizer;
import java.util.Locale;

public final class ArticleSearch {
    private ArticleSearch() {
    }

    public static boolean matches(Article article, String query) {
        if (article == null) {
            return false;
        }
        String normalized = normalize(query);
        if (normalized.length() == 0) {
            return true;
        }
        String haystack = normalize(article.title)
                + "\n" + normalize(article.summary)
                + "\n" + normalize(article.content)
                + "\n" + normalize(article.fullContent)
                + "\n" + normalize(article.author)
                + "\n" + normalize(article.feedTitle)
                + "\n" + normalize(article.category)
                + "\n" + normalize(article.link);
        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            if (token.length() > 0 && haystack.indexOf(token) < 0) {
                return false;
            }
        }
        return true;
    }

    public static String normalize(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutMarks = decomposed.replaceAll("\\p{M}+", "");
        return withoutMarks.toLowerCase(Locale.ROOT).trim();
    }
}
