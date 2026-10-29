package com.rsspro.core;

import com.rsspro.data.Article;
import com.rsspro.data.FilterRule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class FilterEngine {
    public static final class Result {
        public final boolean visible;
        public final boolean notify;

        public Result(boolean visible, boolean notify) {
            this.visible = visible;
            this.notify = notify && visible;
        }
    }

    private static final class CompiledRule {
        final FilterRule rule;
        final Pattern pattern;

        CompiledRule(FilterRule rule, Pattern pattern) {
            this.rule = rule;
            this.pattern = pattern;
        }
    }

    private final List<CompiledRule> rules = new ArrayList<CompiledRule>();
    private final boolean hasShow;
    private final boolean hasNotify;

    public FilterEngine(List<FilterRule> sourceRules) {
        boolean showSeen = false;
        boolean notifySeen = false;
        if (sourceRules != null) {
            for (FilterRule rule : sourceRules) {
                if (rule == null || !rule.enabled || rule.pattern == null || rule.pattern.trim().length() == 0) {
                    continue;
                }
                Pattern pattern = compile(rule);
                if (pattern == null) {
                    continue;
                }
                rules.add(new CompiledRule(rule, pattern));
                if (rule.action == FilterRule.Action.SHOW) {
                    showSeen = true;
                } else if (rule.action == FilterRule.Action.NOTIFY) {
                    notifySeen = true;
                }
            }
        }
        this.hasShow = showSeen;
        this.hasNotify = notifySeen;
    }

    public Result evaluate(Article article) {
        boolean showMatch = false;
        boolean hideMatch = false;
        boolean notifyMatch = false;
        boolean muteMatch = false;

        for (CompiledRule compiled : rules) {
            FilterRule rule = compiled.rule;
            if (!rule.appliesToFeed(article.feedId)) {
                continue;
            }
            if (!matches(article, rule, compiled.pattern)) {
                continue;
            }
            if (rule.action == FilterRule.Action.SHOW) {
                showMatch = true;
            } else if (rule.action == FilterRule.Action.HIDE) {
                hideMatch = true;
            } else if (rule.action == FilterRule.Action.NOTIFY) {
                notifyMatch = true;
            } else if (rule.action == FilterRule.Action.MUTE) {
                muteMatch = true;
            }
        }

        boolean visible = !hideMatch && (!hasShow || showMatch);
        boolean notification = visible && !muteMatch && (!hasNotify || notifyMatch);
        return new Result(visible, notification);
    }

    public boolean isVisible(Article article) {
        return evaluate(article).visible;
    }

    public boolean shouldNotify(Article article) {
        return evaluate(article).notify;
    }

    public static boolean ruleMatches(Article article, FilterRule rule) {
        if (article == null || rule == null || !rule.appliesToFeed(article.feedId)) {
            return false;
        }
        Pattern pattern = compile(rule);
        if (pattern == null) {
            return false;
        }
        return pattern.matcher(fieldValue(article, rule.field)).find();
    }

    public static String validationError(FilterRule rule) {
        if (rule == null || rule.pattern == null || rule.pattern.trim().length() == 0) {
            return "El patrón no puede estar vacío.";
        }
        if (rule.matchType != FilterRule.MatchType.REGEX) {
            return null;
        }
        try {
            Pattern.compile(rule.pattern, flags(rule));
            return null;
        } catch (PatternSyntaxException ex) {
            String description = ex.getDescription();
            return "Regex no válida: " + (description == null ? ex.getMessage() : description);
        }
    }

    private static boolean matches(Article article, FilterRule rule, Pattern pattern) {
        String haystack = fieldValue(article, rule.field);
        Matcher matcher = pattern.matcher(haystack);
        return matcher.find();
    }

    private static String fieldValue(Article article, FilterRule.Field field) {
        if (field == null || field == FilterRule.Field.ANY) {
            return safe(article.title) + "\n"
                    + safe(article.summary) + "\n"
                    + safe(article.content) + "\n"
                    + safe(article.link) + "\n"
                    + safe(article.author) + "\n"
                    + safe(article.category) + "\n"
                    + safe(article.feedTitle);
        }
        switch (field) {
            case TITLE:
                return safe(article.title);
            case SUMMARY:
                return safe(article.summary);
            case CONTENT:
                return safe(article.content);
            case LINK:
                return safe(article.link);
            case AUTHOR:
                return safe(article.author);
            case FEED:
                return safe(article.feedTitle);
            default:
                return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int flags(FilterRule rule) {
        int flags = Pattern.UNICODE_CASE;
        if (!rule.caseSensitive) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        return flags;
    }

    private static Pattern compile(FilterRule rule) {
        if (rule.pattern == null || rule.pattern.trim().length() == 0) {
            return null;
        }
        String expression;
        if (rule.matchType == FilterRule.MatchType.REGEX) {
            expression = rule.pattern;
        } else if (rule.matchType == FilterRule.MatchType.WORD) {
            expression = "(?<!\\w)" + Pattern.quote(rule.pattern) + "(?!\\w)";
        } else {
            expression = Pattern.quote(rule.pattern);
        }
        try {
            return Pattern.compile(expression, flags(rule));
        } catch (PatternSyntaxException ex) {
            return null;
        }
    }
}
