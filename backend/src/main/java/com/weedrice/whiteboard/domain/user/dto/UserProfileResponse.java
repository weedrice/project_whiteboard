package com.weedrice.whiteboard.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class UserProfileResponse {
    private Long userId;
    private String displayName;
    private String profileImageUrl;
    @Schema(nullable = true)
    private LocalDateTime createdAt;
    @Schema(nullable = true)
    private Long postCount;
    @Schema(nullable = true)
    private Long commentCount;
    private boolean profileOverviewPublic;
    private boolean profilePostsPublic;
    private boolean profileCommentsPublic;
    private boolean canViewOverview;
    private boolean canViewPosts;
    private boolean canViewComments;
}
