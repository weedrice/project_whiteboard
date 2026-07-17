package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUser_UserId(Long userId);

    void deleteByUser_UserIdAndEndpoint(Long userId, String endpoint);

    boolean existsByUser_UserIdAndEndpoint(Long userId, String endpoint);

    boolean existsByUser_UserId(Long userId);

    @Modifying
    @Query("DELETE FROM PushSubscription subscription WHERE subscription.subscriptionId IN :subscriptionIds")
    int deleteBySubscriptionIdIn(@Param("subscriptionIds") Collection<Long> subscriptionIds);
}
