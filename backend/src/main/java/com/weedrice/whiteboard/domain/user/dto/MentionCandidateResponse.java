package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MentionCandidateResponse {
    private Long userId;
    private String displayName;
    private String profileImageUrl;

    public static MentionCandidateResponse from(User user) {
        return MentionCandidateResponse.builder()
                .userId(user.getUserId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
