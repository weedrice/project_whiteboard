package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileTemporaryCleanupWorkerTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private EmoticonImageRepository emoticonImageRepository;
    @Mock
    private EmoticonMasterRepository emoticonMasterRepository;

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
    @DisplayName("임시 파일 정리 배치는 이모티콘 참조 파일을 제외하고 삭제 요청한다")
    void requestDeletionBatch_excludesEmoticonReferencedFiles() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 7, 10, 0);
        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 5, 8, 10, 0);
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 0);
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 1);
        LocalDateTime thirdCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 2);
        when(fileRepository.findTemporaryFileCleanupCandidatesAfter(
                eq(cutoff),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 500))))
                .thenReturn(List.of(
                        cleanupCandidate(10L, firstCreatedAt),
                        cleanupCandidate(11L, secondCreatedAt),
                        cleanupCandidate(12L, thirdCreatedAt)));
        when(emoticonImageRepository.findReferencedImageUrls(any()))
                .thenReturn(List.of("/api/v1/files/10"));
        when(emoticonMasterRepository.findReferencedThumbnailUrls(any()))
                .thenReturn(List.of("/files/11"));
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
        when(fileRepository.findTemporaryFileCleanupCandidatesAfter(
                eq(cutoff),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 500))))
                .thenReturn(List.of(
                        cleanupCandidate(10L, firstCreatedAt),
                        cleanupCandidate(11L, secondCreatedAt)));
        when(emoticonImageRepository.findReferencedImageUrls(any()))
                .thenReturn(List.of("/api/v1/files/10"));
        when(emoticonMasterRepository.findReferencedThumbnailUrls(any()))
                .thenReturn(List.of("/files/11"));

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
        when(fileRepository.findTemporaryFileCleanupCandidatesAfter(
                eq(cutoff),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 500))))
                .thenReturn(List.of());

        FileTemporaryCleanupWorker.CleanupBatchResult result = worker.requestDeletionBatch(
                cutoff, null, null, 500, deleteRequestedAt);

        assertThat(result.finished()).isTrue();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.requestedCount()).isZero();
        verifyNoInteractions(emoticonImageRepository, emoticonMasterRepository);
        verify(fileRepository, never()).requestDeletionForTemporaryFiles(any(), any());
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
