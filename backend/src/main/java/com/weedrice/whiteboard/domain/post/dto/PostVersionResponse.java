package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PostVersionResponse {
    private Long versionId;
    private String actionType;
    private String title;
    private LocalDateTime createdAt;

    public static List<PostVersionResponse> from(List<PostVersion> postVersions) {
        return postVersions.stream()
                .map(version -> PostVersionResponse.builder()
                        .versionId(version.getHistoryId())
                        .actionType(version.getVersionType())
                        .title(version.getOriginalTitle() != null ? version.getOriginalTitle() : version.getPost().getTitle())
                        .createdAt(version.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
