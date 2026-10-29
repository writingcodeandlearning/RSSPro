package com.rsspro.data;

public class Article {
    public String id;
    public String feedId;
    public String feedTitle;
    public String title;
    public String link;
    public String imageUrl;
    public String summary;
    public String content;
    public String fullContent;
    public String author;
    public String category;
    public long publishedAt;
    public long fullFetchAt;

    public Article() {
    }

    public String displayTitle() {
        if (title != null && title.trim().length() > 0) {
            return title.trim();
        }
        return "Sin título";
    }

    public String bodyText() {
        if (fullContent != null && fullContent.trim().length() > 0) {
            return fullContent.trim();
        }
        if (content != null && content.trim().length() > 0) {
            return content.trim();
        }
        if (summary != null) {
            return summary.trim();
        }
        return "";
    }
}
