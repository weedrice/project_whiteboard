package com.weedrice.whiteboard.domain.message.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    private User sender;
    private User receiver;
    private Message message;

    @BeforeEach
    void setUp() {
        sender = User.builder()
                .loginId("sender")
                .email("sender@test.com")
                .password("password")
                .displayName("Sender")
                .build();

        receiver = User.builder()
                .loginId("receiver")
                .email("receiver@test.com")
                .password("password")
                .displayName("Receiver")
                .build();

        message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content("Test message")
                .build();
    }

    @Test
    @DisplayName("메시지 생성 시 초기값 확인")
    void createMessage_initialValues() {
        // then
        assertThat(message.getIsRead()).isFalse();
        assertThat(message.getIsDeletedBySender()).isFalse();
        assertThat(message.getIsDeletedByReceiver()).isFalse();
    }

    @Test
    @DisplayName("메시지 읽음 처리")
    void markAsRead_success() {
        // when
        message.markAsRead();

        // then
        assertThat(message.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("발신자 삭제")
    void deleteBySender_success() {
        // when
        message.deleteBySender();

        // then
        assertThat(message.getIsDeletedBySender()).isTrue();
    }

    @Test
    @DisplayName("수신자 삭제")
    void deleteByReceiver_success() {
        // when
        message.deleteByReceiver();

        // then
        assertThat(message.getIsDeletedByReceiver()).isTrue();
    }
}
