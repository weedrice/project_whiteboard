package com.weedrice.whiteboard.domain.badge.repository;

import com.weedrice.whiteboard.domain.badge.entity.UserBadge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long>, UserBadgeRepositoryCustom {
    boolean existsByUser_UserIdAndBadge_BadgeCode(Long userId, String badgeCode);

    @EntityGraph(attributePaths = "badge")
    List<UserBadge> findByUser_UserIdOrderByBadge_SortOrderAscBadge_BadgeCodeAsc(Long userId);

    @EntityGraph(attributePaths = "badge")
    Optional<UserBadge> findByUser_UserIdAndBadge_BadgeCode(Long userId, String badgeCode);

    @EntityGraph(attributePaths = {"user", "badge"})
    List<UserBadge> findByUser_UserIdInAndBadge_BadgeCodeIn(
            Collection<Long> userIds,
            Collection<String> badgeCodes);

}
