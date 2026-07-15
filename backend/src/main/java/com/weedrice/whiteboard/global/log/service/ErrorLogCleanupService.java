package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.global.log.repository.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ErrorLogCleanupService {

    private final ErrorLogRepository errorLogRepository;

    @Transactional
    public int deleteExpiredClientErrors(LocalDateTime cutoff, int batchSize) {
        return deleteBatch(errorLogRepository.findExpiredClientErrorIds(cutoff, PageRequest.of(0, batchSize)));
    }

    @Transactional
    public int deleteExpiredResolvedServerErrors(LocalDateTime cutoff, int batchSize) {
        return deleteBatch(errorLogRepository.findExpiredResolvedServerErrorIds(cutoff, PageRequest.of(0, batchSize)));
    }

    private int deleteBatch(List<Long> errorLogIds) {
        if (errorLogIds.isEmpty()) {
            return 0;
        }
        errorLogRepository.deleteAllByIdInBatch(errorLogIds);
        return errorLogIds.size();
    }
}
