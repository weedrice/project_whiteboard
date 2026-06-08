package com.weedrice.whiteboard.global.util;

import java.util.regex.Pattern;

public final class HtmlSafetyUtils {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "<[^>]+>",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<\\s*script[^>]*>.*?</\\s*script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL
    );
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "on\\w+\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    private HtmlSafetyUtils() {
    }

    public static String stripTags(String value) {
        return value == null ? null : HTML_TAG_PATTERN.matcher(value).replaceAll("");
    }

    public static String removeScriptTags(String value) {
        return value == null ? null : SCRIPT_PATTERN.matcher(value).replaceAll("");
    }

    public static String removeEventHandlers(String value) {
        return value == null ? null : EVENT_HANDLER_PATTERN.matcher(value).replaceAll("");
    }

    public static boolean containsUnsafeHtml(String value) {
        return containsScriptTag(value) || containsEventHandler(value) || containsHtmlTag(value);
    }

    public static boolean containsScriptTag(String value) {
        return hasText(value) && SCRIPT_PATTERN.matcher(value).find();
    }

    public static boolean containsEventHandler(String value) {
        return hasText(value) && EVENT_HANDLER_PATTERN.matcher(value).find();
    }

    public static boolean containsHtmlTag(String value) {
        return hasText(value) && HTML_TAG_PATTERN.matcher(value).find();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
