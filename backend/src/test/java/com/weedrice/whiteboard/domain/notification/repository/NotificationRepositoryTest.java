package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class NotificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository notificationRepository;

    private User user;
    private User otherUser;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .email("test@test.com")
                .password("password")
                .displayName("Test User")
                .build();
        entityManager.persist(user);

        otherUser = User.builder()
                .loginId("otheruser")
                .email("other@test.com")
                .password("password")
                .displayName("Other User")
                .build();
        entityManager.persist(otherUser);

        notification = Notification.builder()
                .user(user)
                .notificationType(NotificationType.COMMENT)
                .sourceType("POST")
                .sourceId(1L)
                .content("Test notification")
                .build();
        entityManager.persist(notification);
        entityManager.flush();
    }

    @Test
    @DisplayName("알림 ID로 조회 성공")
    void findById_success() {
        // when
        Optional<Notification> found = notificationRepository.findById(notification.getNotificationId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("Test notification");
    }

    @Test
    @DisplayName("사용자별 알림 목록 조회 성공")
    void findByUser_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Notification> notifications =
                notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId(), pageRequest);

        // then
        assertThat(notifications.getContent()).isNotEmpty();
        assertThat(notifications.getContent().get(0).getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("동일 생성 시각 알림은 알림 ID 내림차순으로 정렬한다")
    void findByUser_ordersByNotificationIdDescWhenCreatedAtTies() {
        Notification first = persistNotification("First notification");
        Notification second = persistNotification("Second notification");
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        Page<Notification> notifications =
                notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId(), PageRequest.of(0, 10));

        assertThat(notifications.getContent())
                .extracting(Notification::getNotificationId)
                .containsSubsequence(second.getNotificationId(), first.getNotificationId());
    }

    @Test
    @DisplayName("사용자 ID 기반 읽지 않은 알림 개수 조회 성공")
    void countByUserIdAndIsRead_success() {
        long count = notificationRepository.countByUser_UserIdAndIsRead(user.getUserId(), false);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 ID 기반 단건 알림 읽음 처리 성공")
    void markReadByNotificationIdAndUserId_success() {
        int updatedRows = notificationRepository.markReadByNotificationIdAndUserId(
                notification.getNotificationId(),
                user.getUserId());
        entityManager.flush();
        entityManager.clear();

        Notification found = entityManager.find(Notification.class, notification.getNotificationId());

        assertThat(updatedRows).isEqualTo(1);
        assertThat(found.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("Already read owned notification returns success for single read")
    void markReadByNotificationIdAndUserId_alreadyReadOwnedNotification() {
        notification.read();
        entityManager.flush();
        entityManager.clear();

        int updatedRows = notificationRepository.markReadByNotificationIdAndUserId(
                notification.getNotificationId(),
                user.getUserId());
        entityManager.flush();
        entityManager.clear();

        Notification found = entityManager.find(Notification.class, notification.getNotificationId());

        assertThat(updatedRows).isEqualTo(1);
        assertThat(found.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("사용자 ID 기반 전체 읽음 처리는 대상 사용자 알림만 수정한다")
    void readAllByUserId_success() {
        Notification otherNotification = Notification.builder()
                .user(otherUser)
                .notificationType(NotificationType.COMMENT)
                .sourceType("POST")
                .sourceId(2L)
                .content("Other notification")
                .build();
        entityManager.persist(otherNotification);
        entityManager.flush();

        int updatedRows = notificationRepository.readAllByUserId(user.getUserId());
        entityManager.flush();
        entityManager.clear();

        Notification found = entityManager.find(Notification.class, notification.getNotificationId());
        Notification otherFound = entityManager.find(Notification.class, otherNotification.getNotificationId());

        assertThat(updatedRows).isEqualTo(1);
        assertThat(found.getIsRead()).isTrue();
        assertThat(otherFound.getIsRead()).isFalse();
    }

    private Notification persistNotification(String content) {
        Notification notification = Notification.builder()
                .user(user)
                .notificationType(NotificationType.COMMENT)
                .sourceType("POST")
                .sourceId(1L)
                .content(content)
                .build();
        entityManager.persist(notification);
        return notification;
    }

    private void updateCreatedAt(Notification targetNotification, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE notifications SET created_at = :createdAt WHERE notification_id = :notificationId")
                .setParameter("createdAt", createdAt)
                .setParameter("notificationId", targetNotification.getNotificationId())
                .executeUpdate();
    }
}
