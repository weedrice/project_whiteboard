package com.weedrice.whiteboard.domain.moderation.repository;

import com.weedrice.whiteboard.domain.moderation.entity.ModerationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationAuditLogRepository extends JpaRepository<ModerationAuditLog, Long> {

    @EntityGraph(attributePaths = {"actorUser", "board"})
    Page<ModerationAuditLog> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"actorUser", "board"})
    Page<ModerationAuditLog> findByBoard_BoardId(Long boardId, Pageable pageable);
}
