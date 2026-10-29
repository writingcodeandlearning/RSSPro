package com.rsspro.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateUtil {
    private static final String[] PATTERNS = new String[]{
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm Z",
            "EEE, dd MMM yyyy HH:mm z",
            "dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss z",
            "EEE MMM dd HH:mm:ss zzz yyyy",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
    };

    private DateUtil() {
    }

    public static long parse(String raw) {
        if (raw == null) {
            return 0L;
        }
        String value = raw.trim();
        if (value.length() == 0) {
            return 0L;
        }
        value = normalize(value);

        for (String pattern : PATTERNS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            if (pattern.indexOf('Z') >= 0 && pattern.indexOf("yyyy-MM-dd") < 0) {
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            try {
                Date date = format.parse(value);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }

        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            iso.setLenient(false);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = iso.parse(value);
            if (date != null) {
                return date.getTime();
            }
        } catch (ParseException ignored) {
        }
        return 0L;
    }

    private static String normalize(String value) {
        String result = value;
        if (result.endsWith("Z")) {
            result = result.substring(0, result.length() - 1) + "+0000";
        }
        int lastColon = result.lastIndexOf(':');
        int sign = Math.max(result.lastIndexOf('+'), result.lastIndexOf('-'));
        if (sign > 10 && lastColon > sign && lastColon == result.length() - 3) {
            result = result.substring(0, lastColon) + result.substring(lastColon + 1);
        }
        return result;
    }
}
