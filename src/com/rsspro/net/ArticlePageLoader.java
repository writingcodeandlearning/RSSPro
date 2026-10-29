package com.rsspro.net;

import com.rsspro.util.HtmlUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArticlePageLoader {
    public static final class Result {
        public final String text;
        public final String finalUrl;

        public Result(String text, String finalUrl) {
            this.text = text;
            this.finalUrl = finalUrl;
        }
    }

    private static final int MAX_TEXT = 150000;
    private static final int MIN_ARTICLE_TEXT = 80;
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern DROP_BLOCK = Pattern.compile(
            "(?is)<(script|style|noscript|svg|nav|header|footer|aside|form|iframe|button|head)"
                    + "\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern ARTICLE = Pattern.compile(
            "(?is)<article\\b[^>]*>(.*?)</article\\s*>");
    private static final Pattern MAIN = Pattern.compile(
            "(?is)<main\\b[^>]*>(.*?)</main\\s*>");
    private static final Pattern CONTAINER = Pattern.compile(
            "(?is)<(div|section)\\b[^>]*(?:id|class)\\s*=\\s*[\"'][^\"']*"
                    + "(?:article[-_ ]?(?:body|content)|entry[-_ ]?content|post[-_ ]?content|"
                    + "story[-_ ]?body|thread[-_ ]?description|userhtml|content[-_ ]?body)"
                    + "[^\"']*[\"'][^>]*>(.*?)</\\1\\s*>");
    private static final Pattern BODY = Pattern.compile(
            "(?is)<body\\b[^>]*>(.*?)</body\\s*>");
    private static final Pattern BLOCK_END = Pattern.compile(
            "(?i)</(?:p|div|li|h[1-6]|tr|blockquote|section|article|pre|table)\\s*>");
    private static final Pattern BLOCK_START = Pattern.compile(
            "(?i)<(?:li|h[1-6]|blockquote|pre|tr)\\b[^>]*>");
    private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("[\\t\\x0B\\f ]+");
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern JSON_TEXT = Pattern.compile(
            "\"(preparedHtmlDescription|descriptionPurified|articleBody|articleBodyHtml|"
                    + "bodyHtml|contentHtml|descriptionHtml|description)\"\\s*:\\s*\""
                    + "((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern META_TAG = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern META_NAME = Pattern.compile(
            "(?is)\\b(?:name|property)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern META_CONTENT = Pattern.compile(
            "(?is)\\bcontent\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern CHARSET = Pattern.compile(
            "(?i)charset\\s*=\\s*[\"']?([a-zA-Z0-9._-]+)");
    private static final String[] BOILERPLATE = {
            "cookie", "cookies", "publicidad", "anuncio", "suscríbete", "suscribete",
            "iniciar sesión", "iniciar sesion", "registrarse", "compartir", "menú", "menu",
            "newsletter", "todos los derechos reservados", "javascript"
    };

    private ArticlePageLoader() {
    }

    public static Result load(String url) throws IOException {
        FetchResult fetched = FeedFetcher.fetchPage(url);
        String html = decode(fetched.body, fetched.contentType);
        return new Result(extract(html), fetched.finalUrl);
    }

    public static String extract(String html) {
        if (html == null || html.length() == 0) {
            return "";
        }
        String withoutComments = COMMENT.matcher(html).replaceAll(" ");
        String cleaned = DROP_BLOCK.matcher(withoutComments).replaceAll(" ");

        String best = "";
        for (String block : blocks(ARTICLE, cleaned)) {
            best = better(best, toText(block));
        }
        for (String block : blocks(MAIN, cleaned)) {
            best = better(best, toText(block));
        }
        for (String block : blocks(CONTAINER, cleaned)) {
            best = better(best, toText(block));
        }
        if (best.length() > MAX_TEXT) {
            best = best.substring(0, MAX_TEXT);
        }
        if (best.length() >= MIN_ARTICLE_TEXT) {
            return best;
        }

        String embedded = embeddedJsonText(withoutComments);
        String meta = metaDescription(withoutComments);
        String fallback = embedded.length() > 0 ? embedded : meta;
        boolean usedFallback = false;
        if (fallback.length() >= 40 || fallback.length() > best.length()) {
            best = fallback;
            usedFallback = true;
        }
        if (best.length() < 20 && !usedFallback) {
            for (String block : blocks(BODY, cleaned)) {
                best = better(best, toText(block));
            }
        }
        if (best.length() > MAX_TEXT) {
            best = best.substring(0, MAX_TEXT);
        }
        return best;
    }

    private static List<String> blocks(Pattern pattern, String html) {
        List<String> blocks = new ArrayList<String>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            int group = matcher.groupCount() >= 2 ? 2 : 1;
            String value = matcher.group(group);
            if (value != null && value.trim().length() > 0) {
                blocks.add(value);
            }
        }
        return blocks;
    }

    private static String toText(String html) {
        String value = BR.matcher(html).replaceAll("\n");
        value = BLOCK_START.matcher(value).replaceAll("\n");
        value = BLOCK_END.matcher(value).replaceAll("\n");
        value = TAG.matcher(value).replaceAll(" ");
        value = HtmlUtil.decodeEntities(value);
        value = value.replace('\r', '\n');
        value = WHITESPACE.matcher(value).replaceAll(" ");

        LinkedHashSet<String> lines = new LinkedHashSet<String>();
        StringBuilder out = new StringBuilder();
        for (String rawLine : value.split("\\n", -1)) {
            String line = rawLine.trim();
            if (line.length() < 2 || isBoilerplate(line)) {
                continue;
            }
            if (!lines.add(line)) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
            if (out.length() >= MAX_TEXT) {
                break;
            }
        }
        String result = MANY_NEWLINES.matcher(out.toString()).replaceAll("\n\n");
        return result.trim();
    }

    private static String embeddedJsonText(String html) {
        String specific = "";
        String generic = "";
        Matcher matcher = JSON_TEXT.matcher(html);
        while (matcher.find()) {
            String text = toText(unescapeJson(matcher.group(2)));
            if ("description".equalsIgnoreCase(matcher.group(1))) {
                generic = better(generic, text);
            } else {
                specific = better(specific, text);
            }
        }
        return specific.length() > 0 ? specific : generic;
    }

    private static String metaDescription(String html) {
        String best = "";
        Matcher matcher = META_TAG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group(0);
            Matcher nameMatcher = META_NAME.matcher(tag);
            if (!nameMatcher.find()) {
                continue;
            }
            String name = nameMatcher.group(1);
            if (name == null) {
                name = nameMatcher.group(2);
            }
            if (!"description".equalsIgnoreCase(name)
                    && !"og:description".equalsIgnoreCase(name)
                    && !"twitter:description".equalsIgnoreCase(name)) {
                continue;
            }
            Matcher content = META_CONTENT.matcher(tag);
            if (!content.find()) {
                continue;
            }
            String raw = content.group(1);
            if (raw == null) {
                raw = content.group(2);
            }
            String text = toText(HtmlUtil.decodeEntities(raw == null ? "" : raw));
            best = better(best, text);
        }
        return best;
    }

    private static String better(String current, String candidate) {
        if (candidate == null) {
            return current;
        }
        String value = candidate.trim();
        return value.length() > current.length() ? value : current;
    }

    private static boolean isBoilerplate(String line) {
        if (line.length() > 120) {
            return false;
        }
        String value = line.toLowerCase(Locale.ROOT);
        for (String word : BOILERPLATE) {
            if (value.equals(word)) {
                return true;
            }
        }
        return false;
    }

    private static String unescapeJson(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        try {
                            int code = Integer.parseInt(value.substring(i + 1, i + 5), 16);
                            out.append((char) code);
                            i += 4;
                        } catch (NumberFormatException ex) {
                            out.append("\\u");
                        }
                    } else {
                        out.append("\\u");
                    }
                    break;
                default:
                    out.append(next);
                    break;
            }
        }
        return out.toString();
    }

    private static String decode(byte[] body, String contentType) {
        Charset charset = null;
        if (contentType != null) {
            Matcher matcher = CHARSET.matcher(contentType);
            if (matcher.find()) {
                charset = charset(matcher.group(1));
            }
        }
        if (charset == null) {
            int length = Math.min(body.length, 4096);
            String preview = new String(body, 0, length, StandardCharsets.ISO_8859_1);
            Matcher matcher = CHARSET.matcher(preview);
            if (matcher.find()) {
                charset = charset(matcher.group(1));
            }
        }
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        String value = new String(body, charset);
        if (value.length() > 0 && value.charAt(0) == '\uFEFF') {
            value = value.substring(1);
        }
        return value;
    }

    private static Charset charset(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception ex) {
            return null;
        }
    }
}
