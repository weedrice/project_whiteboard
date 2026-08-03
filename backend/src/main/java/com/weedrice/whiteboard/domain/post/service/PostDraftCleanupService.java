package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.constant.PostDraftPolicy;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostDraftCleanupService {

    private static final int CLEANUP_BATCH_SIZE = 100;

    private final DraftPostRepository draftPostRepository;
    private final FileService fileService;
    private final Clock clock;
    private final PostDraftCleanupBatchService cleanupBatchService;
    private final MeterRegistry meterRegistry;

    public int enforceUserDraftLimit(User user) {
        long excessCount = draftPostRepository.countDeletableByUser(user) - PostDraftPolicy.MAX_DRAFTS_PER_USER;
        int deletedCount = 0;

        while (excessCount > 0) {
            int batchSize = (int) Math.min(excessCount, CLEANUP_BATCH_SIZE);
            List<DraftPost> oldestDrafts = draftPostRepository.findOldestByUser(
                    user,
                    PageRequest.of(0, batchSize));
            if (oldestDrafts.isEmpty()) {
                break;
            }
            deleteDrafts(oldestDrafts);
            draftPostRepository.flush();
            deletedCount += oldestDrafts.size();
            excessCount -= oldestDrafts.size();
        }
        recordCleanup("limit", deletedCount);
        return deletedCount;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int cleanupExpiredDrafts() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(PostDraftPolicy.RETENTION_DAYS);
        int deletedCount = 0;

        while (true) {
            int batchDeletedCount = cleanupBatchService.cleanupExpiredBatch(cutoff, CLEANUP_BATCH_SIZE);
            deletedCount += batchDeletedCount;
            if (batchDeletedCount < CLEANUP_BATCH_SIZE) {
                break;
            }
        }

        if (deletedCount > 0) {
            log.info("Deleted {} expired post draft(s)", deletedCount);
        }
        recordCleanup("retention", deletedCount);
        return deletedCount;
    }

    private void recordCleanup(String reason, int count) {
        if (count <= 0) {
            return;
        }
        meterRegistry.counter("noviis.post.draft.cleaned", "reason", reason).increment(count);
    }

    private void deleteDrafts(List<DraftPost> drafts) {
        for (DraftPost draft : drafts) {
            fileService.markDraftFilesDeletionPending(draft.getDraftId());
            draftPostRepository.delete(draft);
        }
    }
}
