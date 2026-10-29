package com.rsspro.data;

public class FilterRule {
    public enum MatchType {
        CONTAINS,
        WORD,
        REGEX
    }

    public enum Field {
        ANY,
        TITLE,
        SUMMARY,
        CONTENT,
        LINK,
        AUTHOR,
        FEED
    }

    public enum Action {
        SHOW,
        HIDE,
        NOTIFY,
        MUTE
    }

    public String id;
    public String name;
    public String pattern;
    public MatchType matchType = MatchType.CONTAINS;
    public Field field = Field.ANY;
    public boolean caseSensitive;
    public String feedId;
    public Action action = Action.SHOW;
    public boolean enabled = true;

    public FilterRule() {
    }

    public boolean appliesToFeed(String candidateFeedId) {
        return feedId == null || feedId.length() == 0 || feedId.equals(candidateFeedId);
    }
}
