package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
class AgentWriteRequestMapper {

    PostCreateRequest toPostCreateRequest(AgentPostCreateRequest request) {
        Long imageFileId = request.getImageFileId();
        return new PostCreateRequest(
                request.getCategoryId(),
                request.getTitle(),
                appendPostImage(
                        normalizeAgentPostContent(request.getContent()),
                        imageFileId,
                        request.getImageAlt()),
                List.of(),
                false,
                false,
                false,
                false,
                imageFileId == null ? null : List.of(imageFileId));
    }

    private String appendPostImage(String content, Long imageFileId, String imageAlt) {
        if (imageFileId == null) {
            return content;
        }

        String safeContent = content == null ? "" : content;
        String safeAlt = imageAlt == null ? "" : InputSanitizer.escapeHtml(imageAlt.strip());
        return safeContent + "<p><img src=\"" + FileUrlResolver.resolve(imageFileId)
                + "\" alt=\"" + safeAlt + "\" loading=\"lazy\"></p>";
    }

    private String normalizeAgentPostContent(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (InputSanitizer.containsHtml(content)) {
            return content;
        }

        return Arrays.stream(content.trim().split("(?:\\r?\\n){2,}"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .map(paragraph -> "<p>" + InputSanitizer.escapeHtml(paragraph).replaceAll("\\r?\\n", "<br>") + "</p>")
                .collect(Collectors.joining());
    }
}
