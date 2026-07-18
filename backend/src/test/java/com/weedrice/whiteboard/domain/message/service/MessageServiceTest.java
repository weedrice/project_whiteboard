package com.weedrice.whiteboard.domain.message.service;

import com.weedrice.whiteboard.domain.message.constant.MessageConstraints;
import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.message.repository.MessageRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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
    private UserWritableResolver userWritableResolver;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private SanctionService sanctionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        givenLockedSenderAndReceiver();
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message savedMessage = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedMessage, "messageId", 1L);
            return savedMessage;
        });

        Long result = messageService.sendMessage(1L, 2L, "Hello!");

        assertThat(result).isEqualTo(1L);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("Hello!");
        assertThat(messageCaptor.getValue().getSender()).isEqualTo(sender);
        assertThat(messageCaptor.getValue().getReceiver()).isEqualTo(receiver);
        verify(sanctionService).validateNotBanned(sender);
        verify(sanctionService).validateNotMuted(sender);
        verify(userBlockService).isEitherDirectionBlocked(1L, 2L);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.getUserToNotify()).isSameAs(receiver);
        assertThat(event.getActor()).isSameAs(sender);
        assertThat(event.getNotificationType()).isEqualTo(NotificationType.MESSAGE);
        assertThat(event.getSourceType()).isEqualTo(NotificationSourceType.MESSAGE);
        assertThat(event.getSourceId()).isEqualTo(1L);
        assertThat(event.getMessageKey()).isEqualTo("notification.message.received");
        assertThat(event.getMessageParams()).containsExactly("");
    }

    @Test
    @DisplayName("message content is sanitized before save")
    void sendMessage_sanitizesContentBeforeSave() {
        givenLockedSenderAndReceiver();
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message savedMessage = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedMessage, "messageId", 1L);
            return savedMessage;
        });

        Long result = messageService.sendMessage(1L, 2L, "<b>Hello</b><script>alert(1)</script>");

        assertThat(result).isEqualTo(1L);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("Helloalert(1)");
    }

    @Test
    @DisplayName("message content blank after sanitizing is rejected")
    void sendMessage_blankAfterSanitizing_invalidInput() {
        assertThatThrownBy(() -> messageService.sendMessage(1L, 2L, "<b></b>"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("message content longer than max after sanitizing is rejected before save")
    void sendMessage_contentOverMaxAfterSanitizing_invalidInput() {
        String content = "<p>" + "a".repeat(MessageConstraints.MAX_CONTENT_LENGTH + 1) + "</p>";

        assertThatThrownBy(() -> messageService.sendMessage(1L, 2L, content))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userWritableResolver, never()).lockUserPairForUpdate(any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("message content exactly max length after sanitizing can be saved")
    void sendMessage_contentAtMaxAfterSanitizing_success() {
        String content = "<p>" + "a".repeat(MessageConstraints.MAX_CONTENT_LENGTH) + "</p>";
        givenLockedSenderAndReceiver();
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message savedMessage = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedMessage, "messageId", 1L);
            return savedMessage;
        });

        Long result = messageService.sendMessage(1L, 2L, content);

        assertThat(result).isEqualTo(1L);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).hasSize(MessageConstraints.MAX_CONTENT_LENGTH);
    }

    @Test
    @DisplayName("차단된 상대에게는 쪽지를 보낼 수 없다")
    void sendMessage_blocked() {
        givenLockedSenderAndReceiver();
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> messageService.sendMessage(1L, 2L, "Hello!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BLOCKED_BY_USER));
    }

    @Test
    @DisplayName("비활성 수신자에게 쪽지를 보낼 수 없다")
    void sendMessage_inactiveReceiver_forbidden() {
        receiver.suspend();
        givenLockedSenderAndReceiver();
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.sendMessage(1L, 2L, "Hello!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(userBlockService).isEitherDirectionBlocked(1L, 2L);
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("MUTE 사용자는 쪽지를 보낼 수 없다")
    void sendMessage_mutedUser_forbidden() {
        givenLockedSenderAndReceiver();
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(sender);

        assertThatThrownBy(() -> messageService.sendMessage(1L, 2L, "Hello!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(messageRepository, never()).save(any());
    }

    private void givenLockedSenderAndReceiver() {
        when(userWritableResolver.lockUserPairForUpdate(1L, 2L))
                .thenReturn(new UserWritableResolver.LockedUserPair(sender, receiver));
    }

    @Test
    @DisplayName("받은 쪽지 목록을 조회한다")
    void getReceivedMessages_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(message), pageable, 1);

        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findReceivedMessagesExcludingBlocked(eq(2L), eq(false), anyList(), any(Pageable.class)))
                .thenReturn(messagePage);

        MessageResponse response = messageService.getReceivedMessages(2L, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent().get(0).getContent()).isEqualTo("Test message");
        verify(userBlockService).getBlockedUserIdsEitherDirection(2L);
    }

    @Test
    @DisplayName("received message content is sanitized for response")
    void getReceivedMessages_sanitizesContentForResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Message unsafeMessage = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content("<img src=x onerror=alert(1)>safe")
                .build();
        ReflectionTestUtils.setField(unsafeMessage, "messageId", 2L);
        Page<Message> messagePage = new PageImpl<>(List.of(unsafeMessage), pageable, 1);

        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findReceivedMessagesExcludingBlocked(eq(2L), eq(false), anyList(), any(Pageable.class)))
                .thenReturn(messagePage);

        MessageResponse response = messageService.getReceivedMessages(2L, pageable);

        assertThat(response.getContent().get(0).getContent()).isEqualTo("safe");
    }

    @Test
    @DisplayName("보낸 쪽지 목록을 조회한다")
    void getSentMessages_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(message), pageable, 1);

        when(userBlockService.getBlockedUserIdsEitherDirection(1L)).thenReturn(Collections.emptyList());
        when(messageRepository.findSentMessagesExcludingBlocked(eq(1L), eq(false), anyList(), any(Pageable.class)))
                .thenReturn(messagePage);

        MessageResponse response = messageService.getSentMessages(1L, pageable);

        assertThat(response).isNotNull();
        verify(userBlockService).getBlockedUserIdsEitherDirection(1L);
    }

    @Test
    @DisplayName("받은 쪽지 목록은 서비스 계층에서 페이지 요청을 제한한다")
    void getReceivedMessages_clampsPageableInService() {
        Pageable requestedPageable = PageRequest.of(0, 1000, Sort.by("messageId"));
        Page<Message> messagePage = new PageImpl<>(List.of(message), requestedPageable, 1);

        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findReceivedMessagesExcludingBlocked(eq(2L), eq(false), anyList(), any(Pageable.class)))
                .thenReturn(messagePage);

        messageService.getReceivedMessages(2L, requestedPageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findReceivedMessagesExcludingBlocked(
                eq(2L),
                eq(false),
                anyList(),
                captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(captor.getValue().getSort().getOrderFor("messageId")).isNull();
    }

    @Test
    @DisplayName("받은 쪽지 목록은 createdAt 오름차순 정렬 요청을 유지한다")
    void getReceivedMessages_preservesCreatedAtAscendingSort() {
        Pageable requestedPageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("createdAt")));
        Page<Message> messagePage = new PageImpl<>(List.of(message), requestedPageable, 1);

        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findReceivedMessagesExcludingBlocked(eq(2L), eq(false), anyList(), any(Pageable.class)))
                .thenReturn(messagePage);

        messageService.getReceivedMessages(2L, requestedPageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findReceivedMessagesExcludingBlocked(
                eq(2L),
                eq(false),
                anyList(),
                captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
    }

    @Test
    @DisplayName("보낸 쪽지 목록은 서비스 계층에서 페이지 요청을 제한한다")
    void getSentMessages_clampsPageableInService() {
        Pageable requestedPageable = PageRequest.of(0, 1000, Sort.by("messageId"));
        Page<Message> messagePage = new PageImpl<>(List.of(message), requestedPageable, 1);

        when(userBlockService.getBlockedUserIdsEitherDirection(1L)).thenReturn(Collections.emptyList());
        when(messageRepository.findSentMessagesExcludingBlocked(eq(1L), eq(false), anyList(), any(Pageable.class)))
                .thenReturn(messagePage);

        messageService.getSentMessages(1L, requestedPageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findSentMessagesExcludingBlocked(
                eq(1L),
                eq(false),
                anyList(),
                captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(captor.getValue().getSort().getOrderFor("messageId")).isNull();
    }

    @Test
    @DisplayName("메시지 상세 조회는 읽음 처리 없이 수행된다")
    void getMessage_success() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findAccessibleMessage(2L, 1L, Collections.emptyList()))
                .thenReturn(Optional.of(message));

        Message result = messageService.getMessage(2L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(1L);
        assertThat(result.getIsRead()).isFalse();
        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(2L);
        verify(userBlockService, never()).isEitherDirectionBlocked(2L, 1L);
    }

    @Test
    @DisplayName("message summary content is sanitized for response")
    void getMessageSummary_sanitizesContentForResponse() {
        Message unsafeMessage = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content("<p>safe</p><script>alert(1)</script>")
                .build();
        ReflectionTestUtils.setField(unsafeMessage, "messageId", 2L);
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findAccessibleMessage(2L, 2L, Collections.emptyList()))
                .thenReturn(Optional.of(unsafeMessage));

        MessageResponse.MessageSummary result = messageService.getMessageSummary(2L, 2L);

        assertThat(result.getContent()).isEqualTo("safealert(1)");
    }

    @Test
    @DisplayName("수신자는 별도 read endpoint로 읽음 처리한다")
    void markAsRead_success() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findAccessibleMessage(2L, 1L, Collections.emptyList()))
                .thenReturn(Optional.of(message));

        messageService.markAsRead(2L, 1L);

        assertThat(message.getIsRead()).isTrue();
        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(2L);
        verify(userBlockService, never()).isEitherDirectionBlocked(2L, 1L);
    }

    @Test
    @DisplayName("발신자는 read endpoint를 호출할 수 없다")
    void markAsRead_senderForbidden() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(messageRepository.findAccessibleMessage(1L, 1L, Collections.emptyList()))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.markAsRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("차단된 대화의 단건 조회는 찾을 수 없음으로 숨긴다")
    void getMessage_blockedConversationNotFound() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));
        when(messageRepository.findAccessibleMessage(2L, 1L, List.of(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessage(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(userBlockService, never()).isEitherDirectionBlocked(2L, 1L);
    }

    @Test
    @DisplayName("차단된 대화의 읽음 처리는 찾을 수 없음으로 숨긴다")
    void markAsRead_blockedConversationNotFound() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));
        when(messageRepository.findAccessibleMessage(2L, 1L, List.of(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.markAsRead(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        assertThat(message.getIsRead()).isFalse();
        verify(userBlockService, never()).isEitherDirectionBlocked(2L, 1L);
    }

    @Test
    @DisplayName("이미 삭제한 메시지는 읽음 처리에서도 찾을 수 없다")
    void markAsRead_deletedByViewerNotFound() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findAccessibleMessage(2L, 1L, Collections.emptyList())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.markAsRead(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("참여자가 아닌 사용자의 읽음 처리는 찾을 수 없음으로 처리된다")
    void markAsRead_nonParticipantNotFound() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(3L)).thenReturn(Collections.emptyList());
        when(messageRepository.findAccessibleMessage(3L, 1L, Collections.emptyList())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.markAsRead(3L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("이미 삭제한 메시지는 단건 조회에서 찾을 수 없다")
    void getMessage_deletedByViewerNotFound() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.findAccessibleMessage(2L, 1L, Collections.emptyList())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessage(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("참여자가 아닌 사용자의 단건 조회는 찾을 수 없음으로 처리된다")
    void getMessage_nonParticipantNotFound() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(3L)).thenReturn(Collections.emptyList());
        when(messageRepository.findAccessibleMessage(3L, 1L, Collections.emptyList())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessage(3L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("읽지 않은 메시지 개수를 조회한다")
    void getUnreadMessageCount_success() {
        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(Collections.emptyList());
        when(messageRepository.countUnreadMessagesExcludingBlocked(eq(2L), eq(false), eq(false), anyList()))
                .thenReturn(5L);

        long count = messageService.getUnreadMessageCount(2L);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("메시지 삭제 성공 - 발신자")
    void deleteMessage_success_sender() {
        when(messageRepository.findDeletableByMessageIdForUpdate(1L, 1L)).thenReturn(Optional.of(message));

        messageService.deleteMessage(1L, 1L);

        verify(messageRepository).findDeletableByMessageIdForUpdate(1L, 1L);
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
        when(messageRepository.findDeletableByMessageIdForUpdate(1L, 2L)).thenReturn(Optional.of(selfMessage));

        messageService.deleteMessage(1L, 2L);

        assertThat(selfMessage.getIsDeletedBySender()).isTrue();
        assertThat(selfMessage.getIsDeletedByReceiver()).isTrue();
        verify(messageRepository).delete(selfMessage);
    }

    @Test
    @DisplayName("메시지 일괄 삭제는 유효 ID만 한 번에 조회하고 참여자 방향대로 처리한다")
    void deleteMessages_rejectsNullIdBeforeRepositoryLookup() {
        Message receivedMessage = Message.builder()
                .sender(receiver)
                .receiver(sender)
                .content("received")
                .build();
        ReflectionTestUtils.setField(receivedMessage, "messageId", 2L);
        assertThatThrownBy(() -> messageService.deleteMessages(1L, Arrays.asList(1L, null, 1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(messageRepository, never()).findDeletableByMessageIdInForUpdate(any(), anyList());
    }

    @Test
    @DisplayName("메시지 일괄 삭제는 null 요청 목록을 INVALID_INPUT_VALUE로 거부한다")
    void deleteMessages_nullRequest_invalidInput() {
        assertThatThrownBy(() -> messageService.deleteMessages(1L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(messageRepository, never()).findDeletableByMessageIdInForUpdate(any(), anyList());
        verify(messageRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("메시지 일괄 삭제는 요청 ID가 로드되지 않으면 NOT_FOUND로 실패한다")
    void deleteMessages_missingLoadedMessage_notFound() {
        when(messageRepository.findDeletableByMessageIdInForUpdate(1L, List.of(1L, 2L))).thenReturn(List.of(message));

        assertThatThrownBy(() -> messageService.deleteMessages(1L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(messageRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("메시지 일괄 삭제는 참여자가 아닌 메시지를 NOT_FOUND로 숨긴다")
    void deleteMessages_nonParticipant_notFound() {
        when(messageRepository.findDeletableByMessageIdInForUpdate(3L, List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> messageService.deleteMessages(3L, List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(messageRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("메시지 일괄 삭제는 양쪽이 삭제된 메시지를 완전 삭제한다")
    void deleteMessages_fullyDeletedMessageDeletesEntity() {
        message.deleteByReceiver();
        when(messageRepository.findDeletableByMessageIdInForUpdate(1L, List.of(1L))).thenReturn(List.of(message));

        messageService.deleteMessages(1L, List.of(1L));

        assertThat(message.getIsDeletedBySender()).isTrue();
        assertThat(message.getIsDeletedByReceiver()).isTrue();
        verify(messageRepository).deleteAll(List.of(message));
    }

    @Test
    @DisplayName("message bulk delete allows exactly 500 distinct ids")
    void deleteMessages_allowsUpToMaxBulkDeleteCount() {
        List<Long> messageIds = LongStream.rangeClosed(1, 500)
                .boxed()
                .toList();
        List<Message> messages = messageIds.stream()
                .map(this::messageWithId)
                .toList();
        when(messageRepository.findDeletableByMessageIdInForUpdate(1L, messageIds)).thenReturn(messages);

        messageService.deleteMessages(1L, messageIds);

        verify(messageRepository).findDeletableByMessageIdInForUpdate(1L, messageIds);
    }

    @Test
    @DisplayName("message bulk delete rejects more than 500 distinct ids")
    void deleteMessages_overMaxBulkDeleteCount_invalidInput() {
        List<Long> messageIds = LongStream.rangeClosed(1, 501)
                .boxed()
                .toList();

        assertThatThrownBy(() -> messageService.deleteMessages(1L, messageIds))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(messageRepository, never()).findDeletableByMessageIdInForUpdate(any(), anyList());
        verify(messageRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("message bulk delete applies max to the raw request before deduplication")
    void deleteMessages_appliesMaxBeforeDistinctIds() {
        List<Long> requestIds = LongStream.rangeClosed(1, 500)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        requestIds.add(null);
        requestIds.add(500L);
        assertThatThrownBy(() -> messageService.deleteMessages(1L, requestIds))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(messageRepository, never()).findDeletableByMessageIdInForUpdate(any(), anyList());
    }

    @Test
    @DisplayName("양방향 차단된 사용자의 받은 쪽지는 목록에서 제외된다")
    void getReceivedMessages_bidirectionalBlocked() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> messagePage = new PageImpl<>(List.of(), pageable, 0);

        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(List.of(1L));
        when(messageRepository.findReceivedMessagesExcludingBlocked(eq(2L), eq(false), eq(List.of(1L)), any(Pageable.class)))
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

        when(userBlockService.getBlockedUserIdsEitherDirection(1L)).thenReturn(List.of(2L));
        when(messageRepository.findSentMessagesExcludingBlocked(eq(1L), eq(false), eq(List.of(2L)), any(Pageable.class)))
                .thenReturn(messagePage);

        MessageResponse response = messageService.getSentMessages(1L, pageable);

        assertThat(response.getContent()).isEmpty();
        verify(userBlockService).getBlockedUserIdsEitherDirection(1L);
    }

    @Test
    @DisplayName("양방향 차단된 사용자의 unread count는 제외된다")
    void getUnreadMessageCount_bidirectionalBlocked() {
        when(userBlockService.getBlockedUserIdsEitherDirection(2L)).thenReturn(List.of(1L));
        when(messageRepository.countUnreadMessagesExcludingBlocked(eq(2L), eq(false), eq(false), eq(List.of(1L))))
                .thenReturn(0L);

        long count = messageService.getUnreadMessageCount(2L);

        assertThat(count).isZero();
        verify(userBlockService).getBlockedUserIdsEitherDirection(2L);
    }

    private Message messageWithId(Long messageId) {
        Message result = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content("message " + messageId)
                .build();
        ReflectionTestUtils.setField(result, "messageId", messageId);
        return result;
    }
}
