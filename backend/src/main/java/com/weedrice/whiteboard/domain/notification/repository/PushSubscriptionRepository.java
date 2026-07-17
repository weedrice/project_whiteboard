package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository
        extends JpaRepository<PushSubscription, Long>, PushSubscriptionRepositoryCustom {
    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUser_UserId(Long userId);

    @Query("""
            SELECT ps
            FROM PushSubscription ps
            JOIN FETCH ps.user u
            WHERE u.userId = :userId
              AND u.status = 'ACTIVE'
              AND u.deletedAt IS NULL
            """)
    List<PushSubscription> findDispatchableByUserId(@Param("userId") Long userId);

    void deleteByUser_UserIdAndEndpoint(Long userId, String endpoint);

    boolean existsByUser_UserIdAndEndpoint(Long userId, String endpoint);

    boolean existsByUser_UserId(Long userId);

    @Modifying
    @Query("DELETE FROM PushSubscription ps WHERE ps.user.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            DELETE FROM push_subscriptions
            WHERE subscription_id = :subscriptionId
              AND user_id = :userId
              AND endpoint = :endpoint
              AND p256dh = :p256dh
              AND auth = :auth
              AND modified_at = :modifiedAt
            """, nativeQuery = true)
    int deleteIfSnapshotMatches(
            @Param("subscriptionId") Long subscriptionId,
            @Param("userId") Long userId,
            @Param("endpoint") String endpoint,
            @Param("p256dh") String p256dh,
            @Param("auth") String auth,
            @Param("modifiedAt") LocalDateTime modifiedAt);
}
