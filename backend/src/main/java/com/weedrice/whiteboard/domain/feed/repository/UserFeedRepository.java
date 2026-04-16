package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserFeedRepository extends JpaRepository<UserFeed, Long> {
    Page<UserFeed> findByTargetUserOrderByCreatedAtDesc(User targetUser, Pageable pageable);

    @Query("""
            SELECT uf.targetUser.userId
            FROM UserFeed uf
            WHERE uf.targetUser.userId IN :targetUserIds
              AND uf.feedType = :feedType
              AND uf.contentType = :contentType
              AND uf.contentId = :contentId
              AND uf.sourceCriteria = :sourceCriteria
              AND uf.criteriaId = :criteriaId
            """)
    List<Long> findExistingTargetUserIds(
            @Param("targetUserIds") Collection<Long> targetUserIds,
            @Param("feedType") String feedType,
            @Param("contentType") String contentType,
            @Param("contentId") Long contentId,
            @Param("sourceCriteria") String sourceCriteria,
            @Param("criteriaId") Long criteriaId);
}
