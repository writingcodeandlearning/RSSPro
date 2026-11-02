package com.rsspro.core;

import com.rsspro.data.FilterRule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FilterBackup {
    private static final String TYPE = "rsspro-filters";
    private static final int VERSION = 1;

    public static String toJson(List<FilterRule> rules) {
        if (rules == null) {
            rules = new ArrayList<FilterRule>();
        }
        try {
            JSONObject root = new JSONObject();
            root.put("type", TYPE);
            root.put("version", VERSION);
            root.put("exportedAt", System.currentTimeMillis());
            JSONArray arr = new JSONArray();
            for (FilterRule rule : rules) {
                if (rule == null) {
                    continue;
                }
                JSONObject o = new JSONObject();
                if (rule.id == null || rule.id.length() == 0) {
                    rule.id = UUID.randomUUID().toString();
                }
                o.put("id", rule.id);
                if (rule.name != null) {
                    o.put("name", rule.name);
                }
                if (rule.pattern != null) {
                    o.put("pattern", rule.pattern);
                }
                if (rule.matchType != null) {
                    o.put("matchType", rule.matchType.name());
                }
                if (rule.field != null) {
                    o.put("field", rule.field.name());
                }
                o.put("caseSensitive", rule.caseSensitive);
                if (rule.feedId != null && rule.feedId.length() > 0) {
                    o.put("feedId", rule.feedId);
                } else {
                    o.put("feedId", JSONObject.NULL);
                }
                if (rule.action != null) {
                    o.put("action", rule.action.name());
                }
                o.put("enabled", rule.enabled);
                arr.put(o);
            }
            root.put("rules", arr);
            return root.toString(4);
        } catch (JSONException e) {
            return null;
        }
    }

    public static List<FilterRule> fromJson(String json) {
        if (json == null || json.trim().length() == 0) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json.trim());
            String type = root.optString("type", "");
            if (!TYPE.equals(type)) {
                return null;
            }
            JSONArray arr = root.optJSONArray("rules");
            if (arr == null) {
                return new ArrayList<FilterRule>();
            }
            List<FilterRule> rules = new ArrayList<FilterRule>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                FilterRule rule = new FilterRule();
                rule.id = o.optString("id", null);
                if (rule.id == null || rule.id.length() == 0) {
                    rule.id = UUID.randomUUID().toString();
                }
                rule.name = o.optString("name", "");
                rule.pattern = o.optString("pattern", "");
                String mt = o.optString("matchType", FilterRule.MatchType.CONTAINS.name());
                try {
                    rule.matchType = FilterRule.MatchType.valueOf(mt);
                } catch (Exception e) {
                    rule.matchType = FilterRule.MatchType.CONTAINS;
                }
                String field = o.optString("field", FilterRule.Field.ANY.name());
                try {
                    rule.field = FilterRule.Field.valueOf(field);
                } catch (Exception e) {
                    rule.field = FilterRule.Field.ANY;
                }
                rule.caseSensitive = o.optBoolean("caseSensitive", false);
                Object fid = o.opt("feedId");
                if (fid == null || fid == JSONObject.NULL) {
                    rule.feedId = null;
                } else {
                    rule.feedId = fid.toString();
                }
                String act = o.optString("action", FilterRule.Action.SHOW.name());
                try {
                    rule.action = FilterRule.Action.valueOf(act);
                } catch (Exception e) {
                    rule.action = FilterRule.Action.SHOW;
                }
                rule.enabled = o.optBoolean("enabled", true);
                rules.add(rule);
            }
            return rules;
        } catch (JSONException e) {
            return null;
        }
    }

    public static void write(OutputStream out, String json) throws IOException {
        if (out == null || json == null) {
            return;
        }
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static String read(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }
}
