package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUser_UserId(Long userId);

    void deleteByUser_UserIdAndEndpoint(Long userId, String endpoint);

    boolean existsByUser_UserIdAndEndpoint(Long userId, String endpoint);

    boolean existsByUser_UserId(Long userId);
}
