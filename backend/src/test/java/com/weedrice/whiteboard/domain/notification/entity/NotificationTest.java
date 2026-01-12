package com.weedrice.whiteboard.domain.notification.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private User user;
    private User actor;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("user")
                .email("user@test.com")
                .password("password")
                .displayName("User")
                .build();

        actor = User.builder()
                .loginId("actor")
                .email("actor@test.com")
                .password("password")
                .displayName("Actor")
                .build();

        notification = Notification.builder()
                .user(user)
                .actor(actor)
                .notificationType("COMMENT")
                .sourceType("POST")
                .sourceId(1L)
                .content("Test notification")
                .build();
    }

    @Test
    @DisplayName("알림 생성 시 초기값 확인")
    void createNotification_initialValues() {
        // then
        assertThat(notification.getIsRead()).isFalse();
    }

    @Test
    @DisplayName("알림 읽음 처리")
    void read_success() {
        // when
        notification.read();

        // then
        assertThat(notification.getIsRead()).isTrue();
    }
}
