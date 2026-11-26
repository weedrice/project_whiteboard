package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.ViewHistorySession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewHistorySessionRepository extends JpaRepository<ViewHistorySession, ViewHistorySession.Id> {
}
