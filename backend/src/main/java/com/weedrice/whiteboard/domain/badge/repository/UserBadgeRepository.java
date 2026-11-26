package com.weedrice.whiteboard.domain.badge.repository;

import com.weedrice.whiteboard.domain.badge.entity.UserBadge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long>, UserBadgeRepositoryCustom {
    boolean existsByUser_UserIdAndBadge_BadgeCode(Long userId, String badgeCode);

    @Query("SELECT ub.badge.badgeCode FROM UserBadge ub WHERE ub.user.userId = ?1 AND ub.badge.badgeCode IN ?2")
    Set<String> findOwnedBadgeCodes(Long userId, Collection<String> badgeCodes);

    @EntityGraph(attributePaths = "badge")
    List<UserBadge> findByUser_UserIdOrderByBadge_SortOrderAscBadge_BadgeCodeAsc(Long userId);

    @EntityGraph(attributePaths = "badge")
    Optional<UserBadge> findByUser_UserIdAndBadge_BadgeCode(Long userId, String badgeCode);

    @EntityGraph(attributePaths = {"user", "badge"})
    List<UserBadge> findByUser_UserIdInAndBadge_BadgeCodeIn(
            Collection<Long> userIds,
            Collection<String> badgeCodes);

}
