package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostDraftCleanupBatchService {

    private final DraftPostRepository draftPostRepository;
    private final FileService fileService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupExpiredBatch(LocalDateTime cutoff, int batchSize) {
        List<DraftPost> expiredDrafts = draftPostRepository.findExpiredBefore(
                cutoff,
                PageRequest.of(0, batchSize));
        for (DraftPost draft : expiredDrafts) {
            fileService.markDraftFilesDeletionPending(draft.getDraftId());
            draftPostRepository.delete(draft);
        }
        draftPostRepository.flush();
        return expiredDrafts.size();
    }
}
