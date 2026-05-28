package com.weedrice.whiteboard.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminInquirySummaryResponse {
    private Long postId;
    private String title;
    private String summary;
    private AuthorInfo author;
    private LocalDateTime createdAt;
    private Boolean inquiryAnswered;

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
        private String profileImageUrl;
    }
}
