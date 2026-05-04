package com.weedrice.whiteboard.domain.message.repository;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class MessageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MessageRepository messageRepository;

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
        entityManager.persist(sender);

        receiver = User.builder()
                .loginId("receiver")
                .email("receiver@test.com")
                .password("password")
                .displayName("Receiver")
                .build();
        entityManager.persist(receiver);

        message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content("Test message")
                .build();
        entityManager.persist(message);
        entityManager.flush();
    }

    @Test
    @DisplayName("메시지 ID로 조회 성공")
    void findById_success() {
        // when
        Optional<Message> found = messageRepository.findById(message.getMessageId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("Test message");
    }

    @Test
    @DisplayName("수신자별 메시지 목록 조회 성공")
    void findByReceiver_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Message> messages = messageRepository.findByReceiverAndIsDeletedByReceiver(receiver, false, pageRequest);

        // then
        assertThat(messages.getContent()).isNotEmpty();
        assertThat(messages.getContent().get(0).getReceiver()).isEqualTo(receiver);
    }

    @Test
    @DisplayName("발신자별 메시지 목록 조회 성공")
    void findBySender_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Message> messages = messageRepository.findBySenderAndIsDeletedBySender(sender, false, pageRequest);

        // then
        assertThat(messages.getContent()).isNotEmpty();
        assertThat(messages.getContent().get(0).getSender()).isEqualTo(sender);
    }

    @Test
    @DisplayName("받은 쪽지 목록은 createdAt 오름차순 정렬을 반영한다")
    void findReceivedMessagesExcludingBlocked_createdAtAsc() {
        Message olderMessage = message;
        Message newerMessage = persistMessage(sender, receiver, "Newer message");
        LocalDateTime baseCreatedAt = LocalDateTime.now().minusHours(1);
        updateCreatedAt(olderMessage, baseCreatedAt);
        updateCreatedAt(newerMessage, baseCreatedAt.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        Page<Message> messages = messageRepository.findReceivedMessagesExcludingBlocked(
                receiver,
                false,
                List.of(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("createdAt"))));

        assertThat(messages.getContent())
                .extracting(Message::getContent)
                .containsExactly("Test message", "Newer message");
    }

    @Test
    @DisplayName("보낸 쪽지 목록은 createdAt 오름차순 정렬을 반영한다")
    void findSentMessagesExcludingBlocked_createdAtAsc() {
        Message olderMessage = message;
        Message newerMessage = persistMessage(sender, receiver, "Newer message");
        LocalDateTime baseCreatedAt = LocalDateTime.now().minusHours(1);
        updateCreatedAt(olderMessage, baseCreatedAt);
        updateCreatedAt(newerMessage, baseCreatedAt.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        Page<Message> messages = messageRepository.findSentMessagesExcludingBlocked(
                sender,
                false,
                List.of(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("createdAt"))));

        assertThat(messages.getContent())
                .extracting(Message::getContent)
                .containsExactly("Test message", "Newer message");
    }

    @Test
    @DisplayName("받은 쪽지 목록은 정렬이 없으면 최신순으로 조회한다")
    void findReceivedMessagesExcludingBlocked_defaultCreatedAtDesc() {
        Message olderMessage = message;
        Message newerMessage = persistMessage(sender, receiver, "Newer message");
        LocalDateTime baseCreatedAt = LocalDateTime.now().minusHours(1);
        updateCreatedAt(olderMessage, baseCreatedAt);
        updateCreatedAt(newerMessage, baseCreatedAt.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        Page<Message> messages = messageRepository.findReceivedMessagesExcludingBlocked(
                receiver,
                false,
                List.of(),
                PageRequest.of(0, 10));

        assertThat(messages.getContent())
                .extracting(Message::getContent)
                .containsExactly("Newer message", "Test message");
    }

    @Test
    @DisplayName("수신자별 읽지 않은 메시지 개수 조회 성공")
    void countUnreadMessages_success() {
        // when
        long count = messageRepository.countByReceiverAndIsReadAndIsDeletedByReceiver(receiver, false, false);

        // then
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("삭제용 잠금 단건 조회는 접근 가능한 송신자와 수신자를 함께 로드한다")
    void findDeletableByMessageIdForUpdate_loadsAccessibleParticipants() {
        entityManager.clear();

        Optional<Message> found = messageRepository.findDeletableByMessageIdForUpdate(
                sender.getUserId(),
                message.getMessageId());

        assertThat(found).isPresent();
        assertThat(found.get().getSender().getUserId()).isEqualTo(sender.getUserId());
        assertThat(found.get().getReceiver().getUserId()).isEqualTo(receiver.getUserId());
    }

    @Test
    @DisplayName("삭제용 잠금 목록 조회는 접근 가능한 메시지를 ID 오름차순으로 로드한다")
    void findDeletableByMessageIdInForUpdate_ordersAccessibleMessagesByMessageId() {
        Message laterMessage = Message.builder()
                .sender(receiver)
                .receiver(sender)
                .content("Later message")
                .build();
        entityManager.persist(laterMessage);
        entityManager.flush();
        entityManager.clear();

        List<Message> found = messageRepository.findDeletableByMessageIdInForUpdate(
                sender.getUserId(),
                List.of(laterMessage.getMessageId(), message.getMessageId()));

        assertThat(found)
                .extracting(Message::getMessageId)
                .containsExactly(message.getMessageId(), laterMessage.getMessageId());
    }

    @Test
    @DisplayName("삭제용 잠금 목록 조회는 접근할 수 없는 메시지를 숨긴다")
    void findDeletableByMessageIdInForUpdate_hidesInaccessibleMessages() {
        User outsider = User.builder()
                .loginId("outsider")
                .email("outsider@test.com")
                .password("password")
                .displayName("Outsider")
                .build();
        entityManager.persist(outsider);
        Message outsiderMessage = Message.builder()
                .sender(outsider)
                .receiver(receiver)
                .content("Hidden message")
                .build();
        entityManager.persist(outsiderMessage);
        entityManager.flush();
        entityManager.clear();

        List<Message> found = messageRepository.findDeletableByMessageIdInForUpdate(
                sender.getUserId(),
                List.of(message.getMessageId(), outsiderMessage.getMessageId()));

        assertThat(found)
                .extracting(Message::getMessageId)
                .containsExactly(message.getMessageId());
    }

    @Test
    @DisplayName("삭제용 잠금 단건 조회는 참여자가 아닌 메시지를 숨긴다")
    void findDeletableByMessageIdForUpdate_nonParticipantHidden() {
        User outsider = User.builder()
                .loginId("outsider")
                .email("outsider@test.com")
                .password("password")
                .displayName("Outsider")
                .build();
        entityManager.persist(outsider);
        entityManager.flush();
        entityManager.clear();

        Optional<Message> result = messageRepository.findDeletableByMessageIdForUpdate(
                outsider.getUserId(),
                message.getMessageId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제용 잠금 단건 조회는 사용자가 이미 삭제한 메시지를 숨긴다")
    void findDeletableByMessageIdForUpdate_deletedByUserHidden() {
        message.deleteBySender();
        entityManager.flush();
        entityManager.clear();

        Optional<Message> hiddenForSender = messageRepository.findDeletableByMessageIdForUpdate(
                sender.getUserId(),
                message.getMessageId());
        Optional<Message> visibleForReceiver = messageRepository.findDeletableByMessageIdForUpdate(
                receiver.getUserId(),
                message.getMessageId());

        assertThat(hiddenForSender).isEmpty();
        assertThat(visibleForReceiver).isPresent();
    }

    @Test
    @DisplayName("단건 접근 조회는 수신자가 삭제한 메시지를 숨긴다")
    void findAccessibleMessage_deletedByReceiver_hiddenFromReceiver() {
        message.deleteByReceiver();
        entityManager.flush();
        entityManager.clear();

        Optional<Message> hiddenForReceiver = messageRepository.findAccessibleMessage(receiver.getUserId(), message.getMessageId());
        Optional<Message> visibleForSender = messageRepository.findAccessibleMessage(sender.getUserId(), message.getMessageId());

        assertThat(hiddenForReceiver).isEmpty();
        assertThat(visibleForSender).isPresent();
    }

    @Test
    @DisplayName("단건 접근 조회는 발신자가 삭제한 메시지를 숨긴다")
    void findAccessibleMessage_deletedBySender_hiddenFromSender() {
        message.deleteBySender();
        entityManager.flush();
        entityManager.clear();

        Optional<Message> hiddenForSender = messageRepository.findAccessibleMessage(sender.getUserId(), message.getMessageId());
        Optional<Message> visibleForReceiver = messageRepository.findAccessibleMessage(receiver.getUserId(), message.getMessageId());

        assertThat(hiddenForSender).isEmpty();
        assertThat(visibleForReceiver).isPresent();
    }

    @Test
    @DisplayName("단건 접근 조회는 참여자만 볼 수 있다")
    void findAccessibleMessage_nonParticipantCannotAccess() {
        User outsider = User.builder()
                .loginId("outsider")
                .email("outsider@test.com")
                .password("password")
                .displayName("Outsider")
                .build();
        entityManager.persist(outsider);
        entityManager.flush();
        entityManager.clear();

        Optional<Message> result = messageRepository.findAccessibleMessage(outsider.getUserId(), message.getMessageId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("자기 자신에게 보낸 메시지는 한쪽 삭제만 되어도 더 이상 단건 접근할 수 없다")
    void findAccessibleMessage_selfMessageHiddenWhenEitherSideDeleted() {
        Message selfMessage = Message.builder()
                .sender(sender)
                .receiver(sender)
                .content("Self message")
                .build();
        entityManager.persist(selfMessage);
        entityManager.flush();

        selfMessage.deleteBySender();
        entityManager.flush();
        entityManager.clear();

        Optional<Message> result = messageRepository.findAccessibleMessage(sender.getUserId(), selfMessage.getMessageId());

        assertThat(result).isEmpty();
    }

    private Message persistMessage(User messageSender, User messageReceiver, String content) {
        Message newMessage = Message.builder()
                .sender(messageSender)
                .receiver(messageReceiver)
                .content(content)
                .build();
        entityManager.persist(newMessage);
        return newMessage;
    }

    private void updateCreatedAt(Message targetMessage, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE messages SET created_at = :createdAt WHERE message_id = :messageId")
                .setParameter("createdAt", createdAt)
                .setParameter("messageId", targetMessage.getMessageId())
                .executeUpdate();
    }
}
