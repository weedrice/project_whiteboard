package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query(value = """
            SELECT n
            FROM Notification n
            LEFT JOIN FETCH n.actor
            LEFT JOIN FETCH n.actorAgent
            WHERE n.user = :user
            ORDER BY n.createdAt DESC
            """, countQuery = """
            SELECT COUNT(n)
            FROM Notification n
            WHERE n.user = :user
            """)
    Page<Notification> findByUserOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);

    long countByUserAndIsRead(User user, Boolean isRead);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    void readAllByUser(@Param("user") User user);
}
