package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.scheduled.service.ScheduledPostCleanupService;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostDraftCleanupCoordinatorTest {

    @Test
    void locksAcrossFailedScheduleAndDraftCleanup() {
        DomainLockManager lockManager = mock(DomainLockManager.class);
        ScheduledPostCleanupService scheduledCleanup = mock(ScheduledPostCleanupService.class);
        PostDraftCleanupService draftCleanup = mock(PostDraftCleanupService.class);
        when(draftCleanup.cleanupExpiredDrafts()).thenReturn(7);
        PostDraftCleanupCoordinator coordinator = new PostDraftCleanupCoordinator(
                lockManager, scheduledCleanup, draftCleanup);

        assertThat(coordinator.cleanupExpiredDrafts()).isEqualTo(7);

        InOrder order = inOrder(lockManager, scheduledCleanup, draftCleanup);
        order.verify(lockManager).lockPostDraftCleanup();
        order.verify(scheduledCleanup).cleanupExpiredFailedPosts();
        order.verify(draftCleanup).cleanupExpiredDrafts();
    }
}
