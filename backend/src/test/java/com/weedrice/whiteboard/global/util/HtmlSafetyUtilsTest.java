package com.weedrice.whiteboard.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSafetyUtilsTest {

    @Test
    @DisplayName("stripTags removes HTML tags and preserves text")
    void stripTags_removesHtmlTags() {
        assertThat(HtmlSafetyUtils.stripTags("<p>Hello <strong>Novi</strong></p>"))
                .isEqualTo("Hello Novi");
    }

    @Test
    @DisplayName("script and event handler patterns are detected as unsafe HTML")
    void containsUnsafeHtml_detectsScriptAndEventHandlers() {
        assertThat(HtmlSafetyUtils.containsUnsafeHtml("<script>alert(1)</script>")).isTrue();
        assertThat(HtmlSafetyUtils.containsUnsafeHtml("<img src=x onerror=alert(1)>")).isTrue();
        assertThat(HtmlSafetyUtils.containsUnsafeHtml("plain text")).isFalse();
    }

    @Test
    @DisplayName("script and event handler removal preserves safe content")
    void removal_preservesSafeContent() {
        String withoutScript = HtmlSafetyUtils.removeScriptTags("<p>safe</p><script>alert(1)</script>");
        String withoutHandler = HtmlSafetyUtils.removeEventHandlers(withoutScript.replace("<p", "<p onclick=evil"));

        assertThat(withoutHandler).contains("<p evil>safe</p>");
        assertThat(withoutHandler).doesNotContain("onclick=");
    }
}
