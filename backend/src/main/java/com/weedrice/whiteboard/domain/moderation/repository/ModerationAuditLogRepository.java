package com.weedrice.whiteboard.domain.moderation.repository;

import com.weedrice.whiteboard.domain.moderation.dto.ModerationAuditLogSearchCondition;
import com.weedrice.whiteboard.domain.moderation.entity.ModerationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModerationAuditLogRepository extends JpaRepository<ModerationAuditLog, Long> {

    @EntityGraph(attributePaths = {"actorUser", "board"})
    @Query("""
            SELECT audit
            FROM ModerationAuditLog audit
            WHERE (:#{#condition.action} IS NULL OR audit.action = :#{#condition.action})
              AND (:#{#condition.actorType} IS NULL OR audit.actorType = :#{#condition.actorType})
              AND (:#{#condition.boardId} IS NULL OR audit.board.boardId = :#{#condition.boardId})
              AND (:#{#condition.boardUrl} IS NULL OR audit.board.boardUrl = :#{#condition.boardUrl})
              AND (:#{#condition.actorUserId} IS NULL OR audit.actorUser.userId = :#{#condition.actorUserId})
              AND (:#{#condition.createdFrom} IS NULL OR audit.createdAt >= :#{#condition.createdFrom})
              AND (:#{#condition.createdTo} IS NULL OR audit.createdAt < :#{#condition.createdTo})
            """)
    Page<ModerationAuditLog> search(@Param("condition") ModerationAuditLogSearchCondition condition, Pageable pageable);
}
