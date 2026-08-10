package com.weedrice.whiteboard.domain.post.support;

import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 게시글 본문 저장 표현과 실제 HTML 사이의 변환을 담당합니다.
 */
public final class PostContentCodec {

    private static final String SANDBOX_MARKER_CLASS = "noviis-sandboxed-post-html";
    private static final Pattern EMPTY_DIV_PATTERN = Pattern.compile("<div\\b[^>]*>\\s*</div>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_ATTRIBUTE_PATTERN = Pattern.compile(
            "\\bclass\\s*=\\s*([\"'])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DATA_VALUE_ATTRIBUTE_PATTERN = Pattern.compile(
            "\\bdata-value\\s*=\\s*([\"'])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private PostContentCodec() {
    }

    public static String expandPreservedHtml(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        Matcher matcher = EMPTY_DIV_PATTERN.matcher(value);
        StringBuilder expanded = new StringBuilder(value.length());
        int previousEnd = 0;
        while (matcher.find()) {
            expanded.append(value, previousEnd, matcher.start());
            expanded.append(decodeMarker(matcher.group()));
            previousEnd = matcher.end();
        }
        return expanded.append(value, previousEnd, value.length()).toString();
    }

    public static String toPlainText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Jsoup.parse(expandPreservedHtml(value)).text().replaceAll("\\s+", " ").trim();
    }

    private static String decodeMarker(String markerHtml) {
        Matcher classAttribute = CLASS_ATTRIBUTE_PATTERN.matcher(markerHtml);
        Matcher dataValueAttribute = DATA_VALUE_ATTRIBUTE_PATTERN.matcher(markerHtml);
        if (!classAttribute.find() || !dataValueAttribute.find()) {
            return markerHtml;
        }
        boolean hasMarkerClass = Arrays.stream(classAttribute.group(2).trim().split("\\s+"))
                .anyMatch(SANDBOX_MARKER_CLASS::equals);
        if (!hasMarkerClass) {
            return markerHtml;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(dataValueAttribute.group(2));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return markerHtml;
        }
    }
}
