package com.weedrice.whiteboard.domain.message.service;

import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.message.repository.MessageRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private SanctionService sanctionService;

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
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message result = messageService.sendMessage(1L, 2L, "Hello!");

        assertThat(result.getContent()).isEqualTo("Hello!");
        assertThat(result.getSender()).isEqualTo(sender);
        assertThat(result.getReceiver()).isEqualTo(receiver);
        verify(sanctionService).validateNotBanned(sender);
        verify(sanctionService).validateNotMuted(sender);
        verify(userBlockService).isEitherDirectionBlocked(1L, 2L);
    }

    @Test
    @DisplayName("차단된 상대에게는 쪽지를 보낼 수 없다")
    void sendMessage_blocked() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> messageService.sendMessage(1L, 2L, "Hello!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BLOCKED_BY_USER));
    }

    @Test
    @DisplayName("MUTE 사용자는 쪽지를 보낼 수 없다")
    void sendMessage_mutedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(sender);

        assertThatThrownBy(() -> messageService.sendMessage(1L, 2L, "Hello!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("받은 쪽지 목록을 조회한다")
    void getReceivedMessages_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(message), pageable, 1);

        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findReceivedMessagesExcludingBlocked(eq(receiver), eq(false), anyList(), eq(pageable)))
                .thenReturn(messagePage);

        MessageResponse response = messageService.getReceivedMessages(2L, pageable);

        assertThat(response).isNotNull();
        verify(userRepository).findById(2L);
    }

    @Test
    @DisplayName("보낸 쪽지 목록을 조회한다")
    void getSentMessages_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(message), pageable, 1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userBlockService.getBlockedUserIdsEitherDirection(1L)).thenReturn(Collections.emptyList());
        when(messageRepository.findSentMessagesExcludingBlocked(eq(sender), eq(false), anyList(), eq(pageable)))
                .thenReturn(messagePage);

        MessageResponse response = messageService.getSentMessages(1L, pageable);

        assertThat(response).isNotNull();
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("메시지 상세 조회는 읽음 처리 없이 수행된다")
    void getMessage_success() {
        when(messageRepository.findAccessibleMessage(2L, 1L)).thenReturn(Optional.of(message));
        when(userBlockService.isEitherDirectionBlocked(2L, 1L)).thenReturn(false);

        Message result = messageService.getMessage(2L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(1L);
        assertThat(result.getIsRead()).isFalse();
        verify(userBlockService).isEitherDirectionBlocked(2L, 1L);
    }

    @Test
    @DisplayName("수신자는 별도 read endpoint로 읽음 처리한다")
    void markAsRead_success() {
        when(messageRepository.findAccessibleMessage(2L, 1L)).thenReturn(Optional.of(message));
        when(userBlockService.isEitherDirectionBlocked(2L, 1L)).thenReturn(false);

        messageService.markAsRead(2L, 1L);

        assertThat(message.getIsRead()).isTrue();
        verify(userBlockService).isEitherDirectionBlocked(2L, 1L);
    }

    @Test
    @DisplayName("발신자는 read endpoint를 호출할 수 없다")
    void markAsRead_senderForbidden() {
        when(messageRepository.findAccessibleMessage(1L, 1L)).thenReturn(Optional.of(message));
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.markAsRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("이미 삭제한 메시지는 읽음 처리에서도 찾을 수 없다")
    void markAsRead_deletedByViewerNotFound() {
        when(messageRepository.findAccessibleMessage(2L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.markAsRead(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("참여자가 아닌 사용자의 읽음 처리는 찾을 수 없음으로 처리된다")
    void markAsRead_nonParticipantNotFound() {
        when(messageRepository.findAccessibleMessage(3L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.markAsRead(3L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("이미 삭제한 메시지는 단건 조회에서 찾을 수 없다")
    void getMessage_deletedByViewerNotFound() {
        when(messageRepository.findAccessibleMessage(2L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessage(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("참여자가 아닌 사용자의 단건 조회는 찾을 수 없음으로 처리된다")
    void getMessage_nonParticipantNotFound() {
        when(messageRepository.findAccessibleMessage(3L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessage(3L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("읽지 않은 메시지 개수를 조회한다")
    void getUnreadMessageCount_success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.countUnreadMessagesExcludingBlocked(eq(receiver), eq(false), eq(false), anyList()))
                .thenReturn(5L);

        long count = messageService.getUnreadMessageCount(2L);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("메시지 삭제 성공 - 발신자")
    void deleteMessage_success_sender() {
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

        messageService.deleteMessage(1L, 1L);

        verify(messageRepository).findById(1L);
    }

    @Test
    @DisplayName("자기 자신에게 보낸 메시지를 삭제하면 양쪽 삭제로 정리된다")
    void deleteMessage_selfMessageDeletesBothSides() {
        Message selfMessage = Message.builder()
                .sender(sender)
                .receiver(sender)
                .content("self")
                .build();
        ReflectionTestUtils.setField(selfMessage, "messageId", 2L);
        when(messageRepository.findById(2L)).thenReturn(Optional.of(selfMessage));

        messageService.deleteMessage(1L, 2L);

        assertThat(selfMessage.getIsDeletedBySender()).isTrue();
        assertThat(selfMessage.getIsDeletedByReceiver()).isTrue();
        verify(messageRepository).delete(selfMessage);
    }

    @Test
    @DisplayName("양방향 차단된 사용자의 받은 쪽지는 목록에서 제외된다")
    void getReceivedMessages_bidirectionalBlocked() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(), pageable, 0);

        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(List.of(1L));
        when(messageRepository.findReceivedMessagesExcludingBlocked(eq(receiver), eq(false), eq(List.of(1L)), eq(pageable)))
                .thenReturn(messagePage);

        MessageResponse response = messageService.getReceivedMessages(2L, pageable);

        assertThat(response.getContent()).isEmpty();
        verify(userBlockService).getBlockedUserIdsEitherDirection(2L);
    }

    @Test
    @DisplayName("양방향 차단된 사용자의 보낸 쪽지는 목록에서 제외된다")
    void getSentMessages_bidirectionalBlocked() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(), pageable, 0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userBlockService.getBlockedUserIdsEitherDirection(1L)).thenReturn(List.of(2L));
        when(messageRepository.findSentMessagesExcludingBlocked(eq(sender), eq(false), eq(List.of(2L)), eq(pageable)))
                .thenReturn(messagePage);

        MessageResponse response = messageService.getSentMessages(1L, pageable);

        assertThat(response.getContent()).isEmpty();
        verify(userBlockService).getBlockedUserIdsEitherDirection(1L);
    }

    @Test
    @DisplayName("양방향 차단된 사용자의 unread count는 제외된다")
    void getUnreadMessageCount_bidirectionalBlocked() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(List.of(1L));
        when(messageRepository.countUnreadMessagesExcludingBlocked(eq(receiver), eq(false), eq(false), eq(List.of(1L))))
                .thenReturn(0L);

        long count = messageService.getUnreadMessageCount(2L);

        assertThat(count).isZero();
        verify(userBlockService).getBlockedUserIdsEitherDirection(2L);
    }
}
