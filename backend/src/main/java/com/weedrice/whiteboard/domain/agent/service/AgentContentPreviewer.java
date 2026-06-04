package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.global.util.InputSanitizer;

final class AgentContentPreviewer {

    private static final int PREVIEW_LENGTH = 120;

    private AgentContentPreviewer() {
    }

    static String preview(String content) {
        if (content == null) {
            return "";
        }
        String plain = InputSanitizer.stripHtml(content).replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.length() <= PREVIEW_LENGTH) {
            return plain;
        }
        return plain.substring(0, PREVIEW_LENGTH);
    }
}
