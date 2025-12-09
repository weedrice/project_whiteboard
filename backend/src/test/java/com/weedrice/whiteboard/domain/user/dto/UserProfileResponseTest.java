package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileResponseTest {

    @Test
    @DisplayName("User 엔티티와 카운트 정보로 UserProfileResponse DTO 생성 성공")
    void buildFromUserAndCounts_createsCorrectDto() {
        // given
        User user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now().minusDays(1));
        user.updateLastLogin();

        long postCount = 15L;
        long commentCount = 30L;

        // when
        UserProfileResponse response = UserProfileResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .build();

        // then
        assertThat(response.getUserId()).isEqualTo(user.getUserId());
        assertThat(response.getLoginId()).isEqualTo(user.getLoginId());
        assertThat(response.getDisplayName()).isEqualTo(user.getDisplayName());
        assertThat(response.getProfileImageUrl()).isEqualTo(user.getProfileImageUrl());
        assertThat(response.getCreatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(response.getLastLoginAt()).isEqualTo(user.getLastLoginAt());
        assertThat(response.getPostCount()).isEqualTo(postCount);
        assertThat(response.getCommentCount()).isEqualTo(commentCount);
    }
}
