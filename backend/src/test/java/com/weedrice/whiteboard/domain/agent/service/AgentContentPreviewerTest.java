package com.weedrice.whiteboard.domain.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentContentPreviewerTest {

    @Test
    void preview_returnsEmptyStringForNull() {
        assertThat(AgentContentPreviewer.preview(null)).isEmpty();
    }

    @Test
    void preview_stripsHtmlAndNormalizesWhitespace() {
        String preview = AgentContentPreviewer.preview("<p>Hello</p>\n<strong>agent</strong>   world");

        assertThat(preview).isEqualTo("Hello agent world");
    }

    @Test
    void preview_truncatesToPreviewLength() {
        String preview = AgentContentPreviewer.preview("a".repeat(121));

        assertThat(preview).hasSize(120);
    }
}
