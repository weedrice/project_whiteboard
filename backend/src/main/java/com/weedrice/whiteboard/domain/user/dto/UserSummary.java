package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.actor.AuthorPresentation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UserSummary {
    private Long userId;
    private String loginId;
    private String displayName;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private boolean withdrawn;

    public UserSummary(Long userId, String loginId, String displayName, String profileImageUrl, LocalDateTime createdAt) {
        this(userId, loginId, displayName, profileImageUrl, createdAt, false);
    }

    public static UserSummary from(User user) {
        boolean withdrawn = AuthorPresentation.isWithdrawn(user.getStatus(), user.getDeletedAt());
        return UserSummary.builder()
                .userId(user.getUserId())
                .loginId(withdrawn ? null : user.getLoginId())
                .displayName(AuthorPresentation.displayName(withdrawn, user.getDisplayName()))
                .profileImageUrl(AuthorPresentation.profileImageUrl(withdrawn, user.getProfileImageUrl()))
                .createdAt(user.getCreatedAt())
                .withdrawn(withdrawn)
                .build();
    }
}
