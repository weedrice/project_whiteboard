package com.weedrice.whiteboard.domain.message.service;

import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.message.repository.MessageRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private MessageService messageService;

    private User sender;
    private User receiver;
    private Message message;

    @BeforeEach
    void setUp() {
        sender = User.builder().loginId("sender").build();
        ReflectionTestUtils.setField(sender, "userId", 1L);
        receiver = User.builder().loginId("receiver").build();
        ReflectionTestUtils.setField(receiver, "userId", 2L);
        
        message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content("Test message")
                .build();
        ReflectionTestUtils.setField(message, "messageId", 1L);
    }

    @Test
    @DisplayName("쪽지 발송 성공")
    void sendMessage_success() {
        // given
        Long senderId = 1L;
        Long receiverId = 2L;
        String content = "Hello!";
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(userBlockService.isBlocked(senderId, receiverId)).thenReturn(false);
        when(userBlockService.isBlocked(receiverId, senderId)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Message result = messageService.sendMessage(senderId, receiverId, content);

        // then
        assertThat(result.getContent()).isEqualTo(content);
        assertThat(result.getSender()).isEqualTo(sender);
        assertThat(result.getReceiver()).isEqualTo(receiver);
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    @DisplayName("받은 메시지 목록 조회 성공")
    void getReceivedMessages_success() {
        // given
        Long userId = 2L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(message), pageable, 1);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(receiver));
        when(userBlockService.getBlockedUserIds(userId)).thenReturn(Collections.emptyList());
        when(messageRepository.findReceivedMessagesExcludingBlocked(eq(receiver), eq(false), anyList(), eq(pageable)))
                .thenReturn(messagePage);

        // when
        MessageResponse response = messageService.getReceivedMessages(userId, pageable);

        // then
        assertThat(response).isNotNull();
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("보낸 메시지 목록 조회 성공")
    void getSentMessages_success() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(message), pageable, 1);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(sender));
        when(userBlockService.getBlockedUserIds(userId)).thenReturn(Collections.emptyList());
        when(messageRepository.findSentMessagesExcludingBlocked(eq(sender), eq(false), anyList(), eq(pageable)))
                .thenReturn(messagePage);

        // when
        MessageResponse response = messageService.getSentMessages(userId, pageable);

        // then
        assertThat(response).isNotNull();
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("메시지 조회 성공")
    void getMessage_success() {
        // given
        Long userId = 2L;
        Long messageId = 1L;
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userBlockService.isBlocked(anyLong(), anyLong())).thenReturn(false);

        // when
        Message result = messageService.getMessage(userId, messageId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
    }

    @Test
    @DisplayName("읽지 않은 메시지 개수 조회 성공")
    void getUnreadMessageCount_success() {
        // given
        Long userId = 2L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(receiver));
        when(userBlockService.getBlockedUserIds(userId)).thenReturn(Collections.emptyList());
        when(messageRepository.countUnreadMessagesExcludingBlocked(eq(receiver), eq(false), eq(false), anyList()))
                .thenReturn(5L);

        // when
        long count = messageService.getUnreadMessageCount(userId);

        // then
        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("메시지 삭제 성공 - 발신자")
    void deleteMessage_success_sender() {
        // given
        Long userId = 1L;
        Long messageId = 1L;
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // when
        messageService.deleteMessage(userId, messageId);

        // then
        verify(messageRepository).findById(messageId);
    }
}
