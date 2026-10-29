package com.rsspro.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlUtil {
    private static final Pattern SCRIPT_STYLE = Pattern.compile(
            "(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern BLOCK_TAG = Pattern.compile(
            "(?i)<\\s*(br|/p|/div|/li|/h[1-6]|/tr|/blockquote)\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");
    private static final Pattern WHITESPACE = Pattern.compile("[\\t\\x0B\\f ]+");
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");

    private HtmlUtil() {
    }

    public static String toPlainText(String html) {
        if (html == null || html.length() == 0) {
            return "";
        }
        String value = html;
        value = SCRIPT_STYLE.matcher(value).replaceAll(" ");
        value = COMMENT.matcher(value).replaceAll(" ");
        value = BLOCK_TAG.matcher(value).replaceAll("\n");
        value = TAG.matcher(value).replaceAll(" ");
        value = decodeEntities(value);
        value = value.replace('\r', '\n');
        value = WHITESPACE.matcher(value).replaceAll(" ");
        value = MANY_NEWLINES.matcher(value).replaceAll("\n\n");
        String[] lines = value.split("\n", -1);
        StringBuilder out = new StringBuilder(value.length());
        for (String line : lines) {
            String trimmed = line.trim();
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(trimmed);
        }
        return out.toString().trim();
    }

    public static String snippet(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String value = text.replaceAll("\\s+", " ").trim();
        if (value.length() <= maxLength) {
            return value;
        }
        int cut = value.lastIndexOf(' ', maxLength);
        if (cut < maxLength / 2) {
            cut = maxLength;
        }
        return value.substring(0, cut).trim() + "...";
    }

    public static String decodeEntities(String value) {
        if (value == null || value.indexOf('&') < 0) {
            return value == null ? "" : value;
        }
        String result = value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&mdash;", "-")
                .replace("&ndash;", "-")
                .replace("&hellip;", "...")
                .replace("&rsquo;", "'")
                .replace("&lsquo;", "'")
                .replace("&rdquo;", "\"")
                .replace("&ldquo;", "\"");

        Matcher matcher = NUMERIC_ENTITY.matcher(result);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String digits = matcher.group(2);
            int codePoint;
            try {
                codePoint = Integer.parseInt(digits, hex.length() > 0 ? 16 : 10);
            } catch (NumberFormatException ex) {
                codePoint = -1;
            }
            String replacement = matcher.group(0);
            if (codePoint > 0 && codePoint <= 0x10FFFF) {
                replacement = new String(Character.toChars(codePoint));
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
