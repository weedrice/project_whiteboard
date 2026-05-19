package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BoardManagerCandidateResponse {
    private Long userId;
    private String loginId;
    private String displayName;
    private String profileImageUrl;

    @JsonProperty("currentManager")
    private boolean currentManager;

    public static BoardManagerCandidateResponse from(User user, boolean currentManager) {
        return BoardManagerCandidateResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .currentManager(currentManager)
                .build();
    }
}
