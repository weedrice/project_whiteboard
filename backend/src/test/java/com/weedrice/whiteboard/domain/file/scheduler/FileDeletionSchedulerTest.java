package com.weedrice.whiteboard.domain.file.scheduler;

import com.weedrice.whiteboard.domain.file.service.FileDeletionWorker;
import com.weedrice.whiteboard.domain.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDeletionSchedulerTest {

    @Mock
    private FileService fileService;
    @Mock
    private FileDeletionWorker fileDeletionWorker;

    @InjectMocks
    private FileDeletionScheduler fileDeletionScheduler;

    @Test
    @DisplayName("Async 제출이 거부되면 claim을 해제한다")
    void processPendingDeletions_releasesClaimWhenSubmissionIsRejected() {
        when(fileService.getPendingDeletionFileIds(40)).thenReturn(List.of(10L));
        when(fileService.getRetryableFailedDeletionFileIds(10)).thenReturn(List.of());
        when(fileDeletionWorker.tryClaim(10L)).thenReturn(true);
        org.mockito.Mockito.doThrow(new TaskRejectedException("rejected"))
                .when(fileDeletionWorker).processDeletion(10L);

        fileDeletionScheduler.processPendingDeletions();

        verify(fileDeletionWorker).releaseClaim(10L);
    }

    @Test
    @DisplayName("claim에 실패한 파일은 워커를 실행하지 않는다")
    void processPendingDeletions_skipsAlreadyClaimedFiles() {
        when(fileService.getPendingDeletionFileIds(40)).thenReturn(List.of(10L));
        when(fileService.getRetryableFailedDeletionFileIds(10)).thenReturn(List.of());
        when(fileDeletionWorker.tryClaim(10L)).thenReturn(false);

        fileDeletionScheduler.processPendingDeletions();

        verify(fileDeletionWorker, never()).processDeletion(10L);
        verify(fileDeletionWorker, never()).releaseClaim(10L);
    }

    @Test
    @DisplayName("pending delete와 retryable failed를 각각 quota만큼 수집한다")
    void processPendingDeletions_collectsSeparateCandidateBatches() {
        when(fileService.getPendingDeletionFileIds(40)).thenReturn(List.of(10L));
        when(fileService.getRetryableFailedDeletionFileIds(10)).thenReturn(List.of(20L));
        when(fileDeletionWorker.tryClaim(10L)).thenReturn(true);
        when(fileDeletionWorker.tryClaim(20L)).thenReturn(true);

        fileDeletionScheduler.processPendingDeletions();

        verify(fileDeletionWorker).processDeletion(10L);
        verify(fileDeletionWorker).processDeletion(20L);
    }
}
