package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
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

    static final int MAX_DRAFTS_PER_USER = 100;
    static final int DRAFT_RETENTION_DAYS = 90;
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final DraftPostRepository draftPostRepository;
    private final FileService fileService;
    private final Clock clock;
    private final PostDraftCleanupBatchService cleanupBatchService;

    public int enforceUserDraftLimit(User user) {
        long excessCount = draftPostRepository.countDeletableByUser(user) - MAX_DRAFTS_PER_USER;
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
        return deletedCount;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int cleanupExpiredDrafts() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(DRAFT_RETENTION_DAYS);
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
        return deletedCount;
    }

    private void deleteDrafts(List<DraftPost> drafts) {
        for (DraftPost draft : drafts) {
            fileService.markDraftFilesDeletionPending(draft.getDraftId());
            draftPostRepository.delete(draft);
        }
    }
}
