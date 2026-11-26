package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostDraftCleanupBatchService {

    private final DraftPostRepository draftPostRepository;
    private final FileService fileService;

    public record BatchResult(int scanned, int deleted) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult cleanupExpiredBatch(LocalDateTime cutoff, int batchSize) {
        List<DraftPost> expiredDrafts = draftPostRepository.findExpiredBefore(
                cutoff,
                PageRequest.of(0, batchSize));
        if (expiredDrafts.isEmpty()) {
            return new BatchResult(0, 0);
        }
        // A reservation can commit while the candidate query waits for the draft lock.
        Set<Long> protectedIds = draftPostRepository.findProtectedDraftIds(
                expiredDrafts.stream().map(DraftPost::getDraftId).toList(), ScheduledPost.PROTECTED_DRAFT_STATUSES);
        int deleted = 0;
        for (DraftPost draft : expiredDrafts) {
            if (protectedIds.contains(draft.getDraftId())) {
                continue;
            }
            fileService.markDraftFilesDeletionPending(draft.getDraftId());
            draftPostRepository.delete(draft);
            deleted++;
        }
        draftPostRepository.flush();
        return new BatchResult(expiredDrafts.size(), deleted);
    }
}
