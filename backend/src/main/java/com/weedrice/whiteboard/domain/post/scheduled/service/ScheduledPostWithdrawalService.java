package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ScheduledPostWithdrawalService {
    private final ScheduledPostRepository scheduledPostRepository;
    private final ScheduledPostFileService scheduledPostFileService;
    private final Clock clock;

    /**
     * The caller holds the owner's user lock, shared with post publication and schedule writes.
     * Canceling PUBLISHING invalidates the lease so an already loaded worker cannot commit a post.
     * Drafts and their files remain under their ordinary retention policy after releasing reservations.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelUnpublishedForLockedUser(Long userId) {
        LocalDateTime canceledAt = LocalDateTime.now(clock);
        for (Long scheduledPostId : scheduledPostRepository.findUnpublishedIdsByUserId(userId)) {
            if (scheduledPostRepository.cancelForWithdrawal(scheduledPostId, userId, canceledAt) == 1) {
                scheduledPostFileService.removeReferences(scheduledPostId);
            }
        }
    }
}
