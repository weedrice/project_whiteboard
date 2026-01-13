package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
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
class NotificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository notificationRepository;

    private User user;
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

        notification = Notification.builder()
                .user(user)
                .notificationType("COMMENT")
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
        Page<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageRequest);

        // then
        assertThat(notifications.getContent()).isNotEmpty();
        assertThat(notifications.getContent().get(0).getUser()).isEqualTo(user);
    }
}
