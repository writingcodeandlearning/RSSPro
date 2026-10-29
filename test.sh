#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="$ROOT/tools/jdk"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
OUT="$ROOT/build/test-classes"

rm -rf "$OUT"
mkdir -p "$OUT"

"$JAVAC" -encoding UTF-8 -d "$OUT" \
    "$ROOT/src/com/rsspro/data/Article.java" \
    "$ROOT/src/com/rsspro/data/Feed.java" \
    "$ROOT/src/com/rsspro/data/FilterRule.java" \
    "$ROOT/src/com/rsspro/data/ParsedFeed.java" \
    "$ROOT/src/com/rsspro/net/FeedParser.java" \
    "$ROOT/src/com/rsspro/net/FeedFetcher.java" \
    "$ROOT/src/com/rsspro/net/FetchResult.java" \
    "$ROOT/src/com/rsspro/util/DateUtil.java" \
    "$ROOT/src/com/rsspro/util/HtmlUtil.java" \
    "$ROOT/src/com/rsspro/core/ArticleSearch.java" \
    "$ROOT/src/com/rsspro/core/FilterEngine.java" \
    "$ROOT/src/com/rsspro/core/FilterSimulator.java" \
    "$ROOT/src/com/rsspro/core/RetentionPolicy.java" \
    "$ROOT/src/com/rsspro/net/ArticlePageLoader.java" \
    "$ROOT/test/TestRunner.java"

"$JAVA" -cp "$OUT" TestRunner
