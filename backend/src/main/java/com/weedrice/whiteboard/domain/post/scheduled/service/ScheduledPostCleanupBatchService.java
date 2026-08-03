package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduledPostCleanupBatchService {

    private final ScheduledPostRepository scheduledPostRepository;
    private final ScheduledPostFileService scheduledPostFileService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupExpiredFailedBatch(LocalDateTime cutoff, LocalDateTime canceledAt, int batchSize) {
        List<ScheduledPost> expiredPosts = scheduledPostRepository.findExpiredFailedBefore(
                cutoff,
                PageRequest.of(0, batchSize));
        for (ScheduledPost scheduledPost : expiredPosts) {
            scheduledPostFileService.removeReferences(scheduledPost.getScheduledPostId());
            scheduledPost.expireFailed(canceledAt);
        }
        scheduledPostRepository.flush();
        return expiredPosts.size();
    }
}
