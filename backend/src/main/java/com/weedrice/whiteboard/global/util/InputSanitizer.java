package com.weedrice.whiteboard.global.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.web.util.HtmlUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 입력값 Sanitization 유틸리티
 * 
 * XSS 공격 방지를 위한 입력값 정제 기능을 제공합니다.
 */
public class InputSanitizer {

    private static final String POST_HTML_BASE_URI = "https://noviis.kr";
    private static final Set<String> POST_HTML_STYLE_PROPERTIES = Set.of(
            "color",
            "background-color",
            "text-align",
            "font-size",
            "line-height",
            "min-width",
            "width"
    );
    private static final Pattern SAFE_DIMENSION_STYLE_VALUE = Pattern.compile(
            "^(?:0|(?:\\d+(?:\\.\\d+)?)(?:px|rem|em|%|ch|vw|vh))$",
            Pattern.CASE_INSENSITIVE);

    private static final Document.OutputSettings POST_HTML_OUTPUT_SETTINGS = new Document.OutputSettings()
            .prettyPrint(false);
    private static final Safelist POST_HTML_SAFELIST = Safelist.none()
            .addTags(
                    "p", "br", "strong", "em", "u", "s", "strike",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "ul", "ol", "li",
                    "blockquote", "code", "pre",
                    "a", "img", "video", "iframe",
                    "div", "span", "sub", "sup",
                    "table", "colgroup", "col", "thead", "tbody", "tfoot", "tr", "th", "td", "hr",
                    "mark")
            .addAttributes(":all", "class", "title", "style")
            .addAttributes("a", "href", "target", "rel")
            .addAttributes("img", "src", "alt", "loading", "width", "height")
            .addAttributes("video", "src", "controls", "width", "height")
            .addAttributes("iframe", "src", "width", "height", "frameborder", "allowfullscreen", "allow",
                    "loading", "referrerpolicy", "sandbox")
            .addAttributes("ol", "start")
            .addAttributes("table", "width")
            .addAttributes("col", "span")
            .addAttributes("th", "colspan", "rowspan", "colwidth")
            .addAttributes("td", "colspan", "rowspan", "colwidth")
            .addAttributes(":all", "data-id", "data-label", "data-type", "data-mention-suggestion-char",
                    "data-mention-user-id", "data-value", "data-video-embed", "data-color")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .addProtocols("video", "src", "http", "https")
            .addProtocols("iframe", "src", "https")
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer")
            .preserveRelativeLinks(true);

    /**
     * HTML 태그를 이스케이프합니다.
     * 
     * @param input 입력 문자열
     * @return 이스케이프된 문자열
     */
    public static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(input);
    }

    /**
     * HTML 태그를 제거합니다.
     * 
     * @param input 입력 문자열
     * @return HTML 태그가 제거된 문자열
     */
    public static String stripHtml(String input) {
        if (input == null) {
            return null;
        }
        return HtmlSafetyUtils.stripTags(input);
    }

    /**
     * 위험한 스크립트와 이벤트 핸들러를 제거합니다.
     * 
     * @param input 입력 문자열
     * @return 정제된 문자열
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input;
        
        // 스크립트 태그 제거
        sanitized = HtmlSafetyUtils.removeScriptTags(sanitized);
        
        // 이벤트 핸들러 제거
        sanitized = HtmlSafetyUtils.removeEventHandlers(sanitized);
        
        return sanitized;
    }

    /**
     * 게시글 본문에 허용된 rich HTML만 남깁니다.
     *
     * @param input 입력 HTML 문자열
     * @return 허용 목록 기반으로 정제된 HTML
     */
    public static String sanitizePostHtml(String input) {
        if (input == null) {
            return null;
        }
        String sanitized = Jsoup.clean(input, POST_HTML_BASE_URI, POST_HTML_SAFELIST, POST_HTML_OUTPUT_SETTINGS);
        Document document = Jsoup.parseBodyFragment(sanitized, POST_HTML_BASE_URI);
        sanitizePostHtmlStyles(document);
        sanitizePostHtmlIframes(document);
        document.outputSettings(POST_HTML_OUTPUT_SETTINGS);
        return document.body().html();
    }

    private static void sanitizePostHtmlStyles(Document document) {
        for (Element element : document.body().getAllElements()) {
            if (!element.hasAttr("style")) {
                continue;
            }
            String sanitizedStyle = sanitizeStyleAttribute(element.attr("style"));
            if (sanitizedStyle.isBlank()) {
                element.removeAttr("style");
            } else {
                element.attr("style", sanitizedStyle);
            }
        }
    }

    private static String sanitizeStyleAttribute(String style) {
        if (style == null || style.isBlank()) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder();
        for (String declaration : style.split(";")) {
            String[] parts = declaration.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String property = parts[0].trim().toLowerCase(Locale.ROOT);
            String value = parts[1].trim();
            if (!POST_HTML_STYLE_PROPERTIES.contains(property) || isUnsafeStyleValue(property, value)) {
                continue;
            }
            if (!sanitized.isEmpty()) {
                sanitized.append("; ");
            }
            sanitized.append(property).append(": ").append(value);
        }
        return sanitized.toString();
    }

    private static boolean isUnsafeStyleValue(String property, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("javascript:")
                || normalized.contains("expression(")
                || normalized.contains("url(")
                || normalized.contains("@import")
                || normalized.contains("behavior:")
                || normalized.contains("-moz-binding")) {
            return true;
        }
        return ("width".equals(property) || "min-width".equals(property))
                && !SAFE_DIMENSION_STYLE_VALUE.matcher(normalized).matches();
    }

    private static void sanitizePostHtmlIframes(Document document) {
        for (Element iframe : document.select("iframe")) {
            if (!VideoEmbedUrlPolicy.isAllowed(iframe.attr("src"))) {
                iframe.remove();
                continue;
            }
            iframe.attr("frameborder", "0");
            iframe.attr("allowfullscreen", "true");
            iframe.attr("loading", "lazy");
            iframe.attr("referrerpolicy", "strict-origin-when-cross-origin");
            iframe.attr("sandbox", "allow-scripts allow-same-origin allow-presentation");
            iframe.attr("allow", "encrypted-media; picture-in-picture");
        }
    }

    /**
     * HTML 태그가 포함되어 있는지 확인합니다.
     * 
     * @param input 입력 문자열
     * @return HTML 태그가 포함되어 있으면 true
     */
    public static boolean containsHtml(String input) {
        return HtmlSafetyUtils.containsHtmlTag(input);
    }

    /**
     * 위험한 스크립트가 포함되어 있는지 확인합니다.
     * 
     * @param input 입력 문자열
     * @return 위험한 스크립트가 포함되어 있으면 true
     */
    public static boolean containsScript(String input) {
        return HtmlSafetyUtils.containsScriptTag(input)
                || HtmlSafetyUtils.containsEventHandler(input);
    }
}
