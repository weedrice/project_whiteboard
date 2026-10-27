package com.weedrice.whiteboard.domain.post.support;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.UnaryOperator;

/**
 * 게시글 본문 저장 표현과 실제 HTML 사이의 변환을 담당합니다.
 */
public final class PostContentCodec {

    public static final String VIEWPORT_LAYER_SELECTOR = "template[data-noviis-layer=viewport]";
    private static final Pattern TEMPLATE_TAG_PATTERN = Pattern.compile("<template(?=[\\s/>])", Pattern.CASE_INSENSITIVE);
    // Same decorative subtree contract as frontend postViewportHtml.ts. Body
    // references outside a viewport template retain their existing semantics.
    private static final Set<String> VIEWPORT_REMOVED_SUBTREES = Set.of(
            "script", "noscript", "meta", "base", "link", "form", "input", "textarea", "button",
            "select", "option", "optgroup", "datalist", "iframe", "frame", "frameset", "object", "embed",
            "svg", "math", "template", "video", "audio", "source", "track", "portal", "fencedframe", "canvas");

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
        return parseVisibleContent(value).text().replaceAll("\\s+", " ").trim();
    }

    public static Document parseVisibleContent(String value) {
        Document document = Jsoup.parse(expandPreservedHtml(value));
        document.select(VIEWPORT_LAYER_SELECTOR).remove();
        return document;
    }

    /** A false positive only causes parsing; quoted/comment text cannot remove content. */
    public static boolean mayContainViewportLayer(String expandedHtml) {
        return expandedHtml.toLowerCase(Locale.ROOT).contains("data-noviis-layer")
                && TEMPLATE_TAG_PATTERN.matcher(expandedHtml).find();
    }

    /** Match browser traversal: body references and one explicit viewport template. */
    public static boolean isSupportedFileReference(Element element) {
        int templates = 0;
        boolean discarded = false;
        for (Element parent : element.parents()) {
            if (parent.normalName().equals("template")) {
                if (++templates > 1 || !parent.attr("data-noviis-layer").equals("viewport")
                        || !parent.tag().namespace().equals("http://www.w3.org/1999/xhtml")) return false;
            } else if (templates == 0 && (VIEWPORT_REMOVED_SUBTREES.contains(parent.normalName())
                    || !parent.tag().namespace().equals("http://www.w3.org/1999/xhtml"))) discarded = true;
        }
        return templates == 0 || (!discarded && element.normalName().equals("img")
                && element.tag().namespace().equals("http://www.w3.org/1999/xhtml"));
    }

    public static String withoutViewportLayers(String value) {
        String expanded = expandPreservedHtml(value);
        if (!mayContainViewportLayer(expanded)) return expanded;
        Document document = Jsoup.parse(expanded);
        var layers = document.select(VIEWPORT_LAYER_SELECTOR);
        if (layers.isEmpty()) return expanded;
        layers.remove();
        document.outputSettings().prettyPrint(false);
        return document.head().html() + document.body().html();
    }

    /** Rewrite file references without exposing preserved HTML to the normal renderer. */
    public static String mapPreservedHtmlPayloads(String value, UnaryOperator<String> transform) {
        if (value == null || value.isEmpty()) return value;
        Matcher matcher = EMPTY_DIV_PATTERN.matcher(value);
        StringBuilder updated = new StringBuilder(value.length());
        int previousEnd = 0;
        while (matcher.find()) {
            updated.append(value, previousEnd, matcher.start());
            String marker = matcher.group();
            String decoded = decodeMarker(marker);
            String transformed = decoded.equals(marker) ? decoded : transform.apply(decoded);
            if (!transformed.equals(decoded)) {
                Matcher data = DATA_VALUE_ATTRIBUTE_PATTERN.matcher(marker);
                if (data.find()) {
                    String encoded = Base64.getEncoder().encodeToString(transformed.getBytes(StandardCharsets.UTF_8));
                    marker = marker.substring(0, data.start(2)) + encoded + marker.substring(data.end(2));
                }
            }
            updated.append(marker);
            previousEnd = matcher.end();
        }
        return updated.append(value, previousEnd, value.length()).toString();
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
