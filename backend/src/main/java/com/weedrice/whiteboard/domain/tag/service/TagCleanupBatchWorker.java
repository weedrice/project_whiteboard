package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class TagCleanupBatchWorker {

    private final TagRepository tagRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteBatch(LocalDateTime cutoff, int batchSize) {
        return tagRepository.deleteOrphanBatch(cutoff, batchSize);
    }
}
