package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileTemporaryCleanupWorkerTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private EmoticonFileReferenceService emoticonFileReferenceService;

    @InjectMocks
    private FileTemporaryCleanupWorker worker;

    @Test
    @DisplayName("임시 파일 정리 배치는 새 트랜잭션에서 실행된다")
    void requestDeletionBatch_usesRequiresNewTransaction() throws Exception {
        Method method = FileTemporaryCleanupWorker.class.getDeclaredMethod(
                "requestDeletionBatch",
                LocalDateTime.class,
                LocalDateTime.class,
                Long.class,
                int.class,
                LocalDateTime.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(FileTemporaryCleanupWorker.class).isPublic();
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("pending upload 정리 요청은 새 트랜잭션에서 실행한다")
    void requestPendingUploadDeletion_usesRequiresNewTransaction() throws Exception {
        Method method = FileTemporaryCleanupWorker.class.getDeclaredMethod(
                "requestPendingUploadDeletion",
                LocalDateTime.class,
                LocalDateTime.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("pending upload 정리 요청을 repository에 위임한다")
    void requestPendingUploadDeletion_delegatesToRepository() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 7, 10, 0);
        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 5, 8, 10, 0);
        when(fileRepository.requestDeletionForStalePendingUploads(cutoff, deleteRequestedAt)).thenReturn(3);

        int requestedCount = worker.requestPendingUploadDeletion(cutoff, deleteRequestedAt);

        assertThat(requestedCount).isEqualTo(3);
        verify(fileRepository).requestDeletionForStalePendingUploads(cutoff, deleteRequestedAt);
    }

    @Test
    @DisplayName("임시 파일 정리 배치는 이모티콘 참조 파일을 제외하고 삭제 요청한다")
    void requestDeletionBatch_excludesEmoticonReferencedFiles() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 7, 10, 0);
        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 5, 8, 10, 0);
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 0);
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 1);
        LocalDateTime thirdCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 2);
        when(fileRepository.findTemporaryFileCleanupCandidates(
                eq(cutoff),
                eq(PageRequest.of(0, 500))))
                .thenReturn(List.of(
                        cleanupCandidate(10L, firstCreatedAt),
                        cleanupCandidate(11L, secondCreatedAt),
                        cleanupCandidate(12L, thirdCreatedAt)));
        when(emoticonFileReferenceService.excludeReferencedFileIds(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(12L));
        when(fileRepository.requestDeletionForTemporaryFiles(List.of(12L), deleteRequestedAt)).thenReturn(1);

        FileTemporaryCleanupWorker.CleanupBatchResult result = worker.requestDeletionBatch(
                cutoff, null, null, 500, deleteRequestedAt);

        assertThat(result.finished()).isFalse();
        assertThat(result.lastCreatedAt()).isEqualTo(thirdCreatedAt);
        assertThat(result.lastFileId()).isEqualTo(12L);
        assertThat(result.candidateCount()).isEqualTo(3);
        assertThat(result.requestedCount()).isEqualTo(1);
        verify(fileRepository).requestDeletionForTemporaryFiles(List.of(12L), deleteRequestedAt);
    }

    @Test
    @DisplayName("임시 파일 정리 배치는 모든 후보가 제외되어도 커서를 전진시킨다")
    void requestDeletionBatch_advancesCursorWhenAllCandidatesExcluded() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 7, 10, 0);
        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 5, 8, 10, 0);
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 0);
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 1);
        when(fileRepository.findTemporaryFileCleanupCandidates(
                eq(cutoff),
                eq(PageRequest.of(0, 500))))
                .thenReturn(List.of(
                        cleanupCandidate(10L, firstCreatedAt),
                        cleanupCandidate(11L, secondCreatedAt)));
        when(emoticonFileReferenceService.excludeReferencedFileIds(List.of(10L, 11L))).thenReturn(List.of());

        FileTemporaryCleanupWorker.CleanupBatchResult result = worker.requestDeletionBatch(
                cutoff, null, null, 500, deleteRequestedAt);

        assertThat(result.finished()).isFalse();
        assertThat(result.lastCreatedAt()).isEqualTo(secondCreatedAt);
        assertThat(result.lastFileId()).isEqualTo(11L);
        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.requestedCount()).isZero();
        verify(fileRepository, never()).requestDeletionForTemporaryFiles(any(), any());
    }

    @Test
    @DisplayName("임시 파일 정리 후보가 없으면 완료 결과를 반환한다")
    void requestDeletionBatch_returnsFinishedWhenNoCandidates() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 7, 10, 0);
        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 5, 8, 10, 0);
        when(fileRepository.findTemporaryFileCleanupCandidates(
                eq(cutoff),
                eq(PageRequest.of(0, 500))))
                .thenReturn(List.of());

        FileTemporaryCleanupWorker.CleanupBatchResult result = worker.requestDeletionBatch(
                cutoff, null, null, 500, deleteRequestedAt);

        assertThat(result.finished()).isTrue();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.requestedCount()).isZero();
        verifyNoInteractions(emoticonFileReferenceService);
        verify(fileRepository, never()).requestDeletionForTemporaryFiles(any(), any());
    }

    @Test
    @DisplayName("첫 배치 이후에는 createdAt/fileId 커서 조회를 사용한다")
    void requestDeletionBatch_usesCursorQueryAfterFirstBatch() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 7, 10, 0);
        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 5, 8, 10, 0);
        LocalDateTime lastCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 0);
        LocalDateTime nextCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 1);
        when(fileRepository.findTemporaryFileCleanupCandidatesAfter(
                cutoff,
                lastCreatedAt,
                10L,
                PageRequest.of(0, 500)))
                .thenReturn(List.of(cleanupCandidate(11L, nextCreatedAt)));
        when(emoticonFileReferenceService.excludeReferencedFileIds(List.of(11L))).thenReturn(List.of());

        FileTemporaryCleanupWorker.CleanupBatchResult result = worker.requestDeletionBatch(
                cutoff, lastCreatedAt, 10L, 500, deleteRequestedAt);

        assertThat(result.lastCreatedAt()).isEqualTo(nextCreatedAt);
        assertThat(result.lastFileId()).isEqualTo(11L);
        verify(fileRepository, never()).findTemporaryFileCleanupCandidates(any(), any());
    }

    private FileRepository.FileCleanupCandidateProjection cleanupCandidate(Long fileId, LocalDateTime createdAt) {
        return new FileRepository.FileCleanupCandidateProjection() {
            @Override
            public Long getFileId() {
                return fileId;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return createdAt;
            }
        };
    }
}
