package com.rsspro.data;

import java.util.ArrayList;
import java.util.List;

public class ParsedFeed {
    public String title;
    public String siteUrl;
    public String imageUrl;
    public final List<Article> articles = new ArrayList<Article>();
}
