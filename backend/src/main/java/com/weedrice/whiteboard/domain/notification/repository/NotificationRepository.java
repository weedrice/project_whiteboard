package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query(value = """
            SELECT n
            FROM Notification n
            LEFT JOIN FETCH n.actor
            LEFT JOIN FETCH n.actorAgent
            WHERE n.user.userId = :userId
            ORDER BY n.lastEventAt DESC, n.notificationId DESC
            """, countQuery = """
            SELECT COUNT(n)
            FROM Notification n
            WHERE n.user.userId = :userId
            """)
    Page<Notification> findByUser_UserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    long countByUser_UserIdAndIsRead(Long userId, Boolean isRead);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findFirstByUser_UserIdAndGroupKeyAndIsReadOrderByLastEventAtDescNotificationIdDesc(
            Long userId,
            String groupKey,
            Boolean isRead);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.userId = :userId AND n.isRead = false")
    int readAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.isRead = true
            WHERE n.notificationId = :notificationId
              AND n.user.userId = :userId
            """)
    int markReadByNotificationIdAndUserId(
            @Param("notificationId") Long notificationId,
            @Param("userId") Long userId);
}
