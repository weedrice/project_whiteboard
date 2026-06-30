package com.weedrice.whiteboard.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputSanitizerTest {

    @Test
    @DisplayName("게시글 본문 sanitizer는 허용된 rich HTML을 유지한다")
    void sanitizePostHtml_preservesAllowedRichHtml() {
        String input = """
                <h2>Title</h2><p class="ql-align-center">Hello <strong>world</strong></p>
                <ul><li>one</li></ul><img src="/api/v1/files/10" alt="image" loading="lazy">
                <a href="https://example.com">link</a>
                <hr>
                """;

        String sanitized = InputSanitizer.sanitizePostHtml(input);

        assertThat(sanitized).contains("<h2>Title</h2>");
        assertThat(sanitized).contains("<strong>world</strong>");
        assertThat(sanitized).contains("<li>one</li>");
        assertThat(sanitized).contains("src=\"/api/v1/files/10\"");
        assertThat(sanitized).contains("loading=\"lazy\"");
        assertThat(sanitized).contains("href=\"https://example.com\"");
        assertThat(sanitized).contains("<hr>");
    }

    @Test
    @DisplayName("게시글 본문 sanitizer는 스크립트와 이벤트 속성을 제거한다")
    void sanitizePostHtml_removesScriptAndEventHandlers() {
        String input = "<p onclick=\"alert(1)\">safe</p><script>alert(1)</script>";

        String sanitized = InputSanitizer.sanitizePostHtml(input);

        assertThat(sanitized).contains("<p>safe</p>");
        assertThat(sanitized).doesNotContain("onclick");
        assertThat(sanitized).doesNotContain("<script");
    }

    @Test
    @DisplayName("게시글 본문 sanitizer는 샌드박스 HTML 마커의 인코딩 값을 유지한다")
    void sanitizePostHtml_preservesSandboxedHtmlMarker() {
        String input = """
                <div class="noviis-sandboxed-post-html" data-value="7JeY6raM"></div>
                <script>alert(1)</script>
                """;

        String sanitized = InputSanitizer.sanitizePostHtml(input);

        assertThat(sanitized).contains("class=\"noviis-sandboxed-post-html\"");
        assertThat(sanitized).contains("data-value=\"7JeY6raM\"");
        assertThat(sanitized).doesNotContain("<script");
    }

    @Test
    @DisplayName("게시글 본문 sanitizer는 위험한 URL scheme을 제거한다")
    void sanitizePostHtml_removesDangerousUrlSchemes() {
        String input = """
                <a href="javascript:alert(1)">bad link</a>
                <img src="javascript:alert(1)" alt="bad">
                <iframe src="javascript:alert(1)"></iframe>
                """;

        String sanitized = InputSanitizer.sanitizePostHtml(input);

        assertThat(sanitized).contains(">bad link</a>");
        assertThat(sanitized).doesNotContain("javascript:");
        assertThat(sanitized).doesNotContain("src=\"javascript:");
        assertThat(sanitized).doesNotContain("href=\"javascript:");
    }

    @Test
    @DisplayName("게시글 본문 sanitizer는 안전한 style만 유지한다")
    void sanitizePostHtml_keepsOnlySafeStyles() {
        String input = """
                <p style="color:#fff; text-align:center; background:url(javascript:alert(1)); width:100px">safe</p>
                <object data="x"></object>
                """;

        String sanitized = InputSanitizer.sanitizePostHtml(input);

        assertThat(sanitized).contains("style=\"color: #fff; text-align: center\"");
        assertThat(sanitized).doesNotContain("background:");
        assertThat(sanitized).doesNotContain("url(");
        assertThat(sanitized).doesNotContain("width:");
        assertThat(sanitized).doesNotContain("<object");
    }

    @Test
    @DisplayName("게시글 본문 sanitizer는 허용된 영상 iframe만 유지한다")
    void sanitizePostHtml_keepsOnlyAllowedVideoIframes() {
        String input = """
                <iframe src="https://www.youtube.com/embed/abc123"></iframe>
                <iframe src="https://evil.example/embed/abc123"></iframe>
                """;

        String sanitized = InputSanitizer.sanitizePostHtml(input);

        assertThat(sanitized).contains("https://www.youtube.com/embed/abc123");
        assertThat(sanitized).doesNotContain("evil.example");
    }
}
