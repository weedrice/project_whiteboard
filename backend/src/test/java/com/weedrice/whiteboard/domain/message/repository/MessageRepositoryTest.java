package com.weedrice.whiteboard.domain.message.repository;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.springframework.context.annotation.Import;

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
    @DisplayName("수신자별 읽지 않은 메시지 개수 조회 성공")
    void countUnreadMessages_success() {
        // when
        long count = messageRepository.countByReceiverAndIsReadAndIsDeletedByReceiver(receiver, false, false);

        // then
        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
