package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDeletionWorkerTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private EmoticonFileReferenceService emoticonFileReferenceService;

    private FileDeletionWorker fileDeletionWorker;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);
        fileDeletionWorker = new FileDeletionWorker(
                fileRepository,
                fileStorageService,
                transactionTemplate,
                emoticonFileReferenceService,
                clock);
    }

    @Test
    @DisplayName("스토리지 삭제 성공 시 파일 레코드를 최종 삭제한다")
    void processDeletion_deletesFileAfterStorageSuccess() {
        File file = pendingFile();

        when(fileRepository.findDeletionClaimCandidateForUpdate(eq(10L), eq(5), any()))
                .thenReturn(Optional.of(file));
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file), Optional.of(file));
        executeTransactions();

        fileDeletionWorker.processDeletion(10L);

        verify(fileStorageService).deleteFileOrThrow("stored.jpg");
        verify(fileRepository).delete(file);
    }

    @Test
    @DisplayName("이모티콘에서 참조 중인 pending 파일은 삭제하지 않고 active로 복구한다")
    void processDeletion_restoresEmoticonReferencedFile() {
        File file = pendingFile();
        ReflectionTestUtils.setField(file, "deleteRetryCount", 2);

        when(fileRepository.findDeletionClaimCandidateForUpdate(eq(10L), eq(5), any()))
                .thenReturn(Optional.of(file));
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file));
        when(emoticonFileReferenceService.isReferenced(10L)).thenReturn(true);
        executeTransactionCallbackOnly();

        fileDeletionWorker.processDeletion(10L);

        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(file.getDeleteRequestedAt()).isNull();
        assertThat(file.getDeleteRetryCount()).isZero();
        verify(fileStorageService, never()).deleteFileOrThrow(any());
        verify(fileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("stale deleting 파일은 이모티콘 참조를 확인하지 않고 삭제를 이어간다")
    void processDeletion_deletesStaleDeletingFileWithoutReferenceCheck() {
        File file = deletingFile();

        when(fileRepository.findDeletionClaimCandidateForUpdate(eq(10L), eq(5), any()))
                .thenReturn(Optional.of(file));
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file), Optional.of(file));
        executeTransactions();

        fileDeletionWorker.processDeletion(10L);

        verify(fileStorageService).deleteFileOrThrow("stored.jpg");
        verify(fileRepository).delete(file);
        verify(emoticonFileReferenceService, never()).isReferenced(any());
        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.DELETING);
        assertThat(file.getDeleteRetryCount()).isZero();
    }

    @Test
    @DisplayName("스토리지 삭제 실패 시 파일 상태를 DELETE_FAILED로 전환한다")
    void processDeletion_marksFileFailedWhenStorageDeleteFails() {
        File file = pendingFile();

        when(fileRepository.findDeletionClaimCandidateForUpdate(eq(10L), eq(5), any()))
                .thenReturn(Optional.of(file));
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file), Optional.of(file));
        executeTransactions();
        doThrow(new BusinessException(ErrorCode.FILE_DELETE_ERROR))
                .when(fileStorageService).deleteFileOrThrow(eq("stored.jpg"));

        fileDeletionWorker.processDeletion(10L);

        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.DELETE_FAILED);
        assertThat(file.getDeleteRetryCount()).isEqualTo(1);
        verify(fileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("스토리지 삭제 후 DB 정리 실패는 DELETE_FAILED로 전환하지 않는다")
    void processDeletion_doesNotMarkFailedWhenFinalizeFailsAfterStorageDelete() {
        File file = pendingFile();

        when(fileRepository.findDeletionClaimCandidateForUpdate(eq(10L), eq(5), any()))
                .thenReturn(Optional.of(file));
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doThrow(new IllegalStateException("db unavailable"))
                .when(transactionTemplate)
                .executeWithoutResult(any());

        fileDeletionWorker.processDeletion(10L);

        verify(fileStorageService).deleteFileOrThrow("stored.jpg");
        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.DELETING);
        assertThat(file.getDeleteRetryCount()).isZero();
        verify(fileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("스토리지 삭제 후에는 이모티콘 참조를 다시 검사하지 않고 파일 레코드를 삭제한다")
    void processDeletion_doesNotRecheckReferenceAfterStorageDelete() {
        File file = pendingFile();

        when(fileRepository.findDeletionClaimCandidateForUpdate(eq(10L), eq(5), any()))
                .thenReturn(Optional.of(file));
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file), Optional.of(file));
        when(emoticonFileReferenceService.isReferenced(10L)).thenReturn(false);
        executeTransactions();

        fileDeletionWorker.processDeletion(10L);

        verify(fileStorageService).deleteFileOrThrow("stored.jpg");
        verify(fileRepository).delete(file);
        verify(emoticonFileReferenceService, times(1)).isReferenced(10L);
        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.DELETING);
        assertThat(file.getDeleteRetryCount()).isZero();
    }

    @Test
    @DisplayName("DB claim에 실패하면 스토리지 삭제를 실행하지 않는다")
    void processDeletion_skipsStorageDeleteWhenClaimFails() {
        when(fileRepository.findDeletionClaimCandidateForUpdate(eq(10L), eq(5), any()))
                .thenReturn(Optional.empty());
        executeTransactionCallbackOnly();

        fileDeletionWorker.processDeletion(10L);

        verify(fileStorageService, never()).deleteFileOrThrow(any());
        verify(fileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("스토리지 삭제 후 파일 삭제 조건이 바뀌면 DB row를 삭제하지 않는다")
    void processDeletion_doesNotDeleteRowWhenDeletionSnapshotChanges() {
        File file = pendingFile();
        File changedFile = pendingFile();
        changedFile.markDeleting(LocalDateTime.of(2026, 5, 7, 0, 0));
        changedFile.updateRelatedInfo(99L, "POST_CONTENT");

        when(fileRepository.findDeletionClaimCandidateForUpdate(eq(10L), eq(5), any()))
                .thenReturn(Optional.of(file));
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file), Optional.of(changedFile));
        executeTransactions();

        fileDeletionWorker.processDeletion(10L);

        verify(fileStorageService).deleteFileOrThrow("stored.jpg");
        verify(fileRepository, never()).delete(any());
    }

    private File pendingFile() {
        File file = File.builder()
                .filePath("stored.jpg")
                .originalName("stored.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(com.weedrice.whiteboard.domain.user.entity.User.builder().build())
                .storageStatus(FileStorageStatus.PENDING_DELETE)
                .build();
        ReflectionTestUtils.setField(file, "fileId", 10L);
        return file;
    }

    private File deletingFile() {
        File file = File.builder()
                .filePath("stored.jpg")
                .originalName("stored.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(com.weedrice.whiteboard.domain.user.entity.User.builder().build())
                .storageStatus(FileStorageStatus.DELETING)
                .deleteRequestedAt(LocalDateTime.of(2026, 5, 6, 23, 0))
                .build();
        ReflectionTestUtils.setField(file, "fileId", 10L);
        return file;
    }

    private void executeTransactions() {
        executeTransactionCallbackOnly();
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private void executeTransactionCallbackOnly() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }
}
