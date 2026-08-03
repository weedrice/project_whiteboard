package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.scheduled.service.ScheduledPostCleanupService;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostDraftCleanupCoordinator {

    private final DomainLockManager domainLockManager;
    private final ScheduledPostCleanupService scheduledPostCleanupService;
    private final PostDraftCleanupService postDraftCleanupService;

    @Transactional
    public int cleanupExpiredDrafts() {
        domainLockManager.lockPostDraftCleanup();
        scheduledPostCleanupService.cleanupExpiredFailedPosts();
        return postDraftCleanupService.cleanupExpiredDrafts();
    }
}
