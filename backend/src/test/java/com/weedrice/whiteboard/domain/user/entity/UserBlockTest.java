package com.weedrice.whiteboard.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserBlockTest {

    @Test
    @DisplayName("UserBlock 생성 성공")
    void createUserBlock_success() {
        // given
        User user = User.builder().loginId("user").build();
        User targetUser = User.builder().loginId("target").build();

        // when
        UserBlock userBlock = UserBlock.builder()
                .user(user)
                .target(targetUser)
                .build();

        // then
        assertThat(userBlock.getUser()).isEqualTo(user);
        assertThat(userBlock.getTarget()).isEqualTo(targetUser);
    }
}
