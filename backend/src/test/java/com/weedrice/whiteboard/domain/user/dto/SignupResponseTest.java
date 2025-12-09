package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SignupResponseTest {

    @Test
    @DisplayName("User 엔티티에서 SignupResponse DTO 생성 성공")
    void fromUserEntity_createsCorrectDto() {
        // given
        User user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        // when
        SignupResponse response = SignupResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .build();

        // then
        assertThat(response.getUserId()).isEqualTo(user.getUserId());
        assertThat(response.getLoginId()).isEqualTo(user.getLoginId());
        assertThat(response.getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getDisplayName()).isEqualTo(user.getDisplayName());
    }
}
