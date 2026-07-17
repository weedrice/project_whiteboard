package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserKeywordSubscriptionRepository extends JpaRepository<UserKeywordSubscription, Long>,
        UserKeywordSubscriptionRepositoryCustom {
    long countByUser_UserId(Long userId);

    boolean existsByUser_UserIdAndKeyword(Long userId, String keyword);

    Optional<UserKeywordSubscription> findByUser_UserIdAndKeyword(Long userId, String keyword);

    List<UserKeywordSubscription> findByUser_UserIdOrderByCreatedAtDescSubscriptionIdDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT subscription
            FROM UserKeywordSubscription subscription
            WHERE LOWER(:title) LIKE CONCAT('%', LOWER(subscription.keyword), '%')
            """)
    List<UserKeywordSubscription> findMatchingTitle(@Param("title") String title);
}
