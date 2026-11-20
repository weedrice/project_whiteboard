package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileVariantStateCommandTest {

    @Mock
    private FileVariantRepository repository;
    @Mock
    private FileRepository files;
    @Mock
    private FileStorageService storage;
    @InjectMocks
    private FileVariantStateCommand command;

    @Test
    void storeAndActivate_writesPendingIntentBeforeActivation() {
        FileVariant variant = variant(FileStorageStatus.PENDING_UPLOAD);
        when(files.findByIdForUpdate(1L)).thenReturn(Optional.of(variant.getFile()));
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));
        byte[] contents = {1, 2};

        command.storeAndActivate(1L, 3L, contents);

        verify(storage).storeBytesAs(contents, "image/webp", "variants/1/thumbnail.webp");
        assertThat(variant.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
    }

    @Test
    void deletingOriginalRefusesNewIntentAndPreviouslyPreparedWrite() {
        File original = variant(FileStorageStatus.PENDING_UPLOAD).getFile();
        ReflectionTestUtils.setField(original, "storageStatus", FileStorageStatus.DELETING);
        when(files.findByIdForUpdate(1L)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> command.createPending(original, FileVariantType.THUMBNAIL, "p", 2, "image/webp", 1, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> command.storeAndActivate(1L, 3L, new byte[2]))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository, storage);
    }

    @Test
    void claimCleanup_marksStalePendingIntentBeforeExternalDelete() {
        FileVariant variant = variant(FileStorageStatus.PENDING_UPLOAD);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 17, 0, 0);
        ReflectionTestUtils.setField(variant, "createdAt", createdAt);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));

        FileVariantStateCommand.FileVariantCleanupSnapshot snapshot = command.claimCleanup(
                3L,
                createdAt.plusHours(2),
                createdAt.plusHours(3),
                10);

        assertThat(snapshot.fileVariantId()).isEqualTo(3L);
        assertThat(variant.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
    }

    @Test
    void claimCleanup_rechecksBackoffAfterLockAndRejectsStaleCandidate() {
        FileVariant variant = variant(FileStorageStatus.PENDING_DELETE);
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 3, 0);
        variant.recordCleanupFailure("failure", now.plusMinutes(10));
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));

        assertThat(command.claimCleanup(3L, now.minusHours(2), now, 10)).isNull();
    }

    @Test
    void deleteClaimed_removesIntentOnlyAfterStorageDeleteSucceeded() {
        FileVariant variant = variant(FileStorageStatus.PENDING_DELETE);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));

        assertThat(command.deleteClaimed(new FileVariantStateCommand.FileVariantCleanupSnapshot(3L, 0))).isTrue();

        var deletion = org.mockito.Mockito.inOrder(storage, repository);
        deletion.verify(storage).deleteFileOrThrow("variants/1/thumbnail.webp");
        deletion.verify(repository).delete(variant);
    }

    @Test
    void failedObjectDeletionLeavesTheClaimForRetry() {
        FileVariant variant = variant(FileStorageStatus.PENDING_DELETE);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));
        doThrow(new IllegalStateException("unavailable")).when(storage).deleteFileOrThrow(variant.getFilePath());
        var claim = new FileVariantStateCommand.FileVariantCleanupSnapshot(3L, 0);

        assertThatThrownBy(() -> command.deleteClaimed(claim)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).delete(variant);

        LocalDateTime nextAttempt = LocalDateTime.of(2026, 11, 20, 10, 0);
        assertThat(command.recordCleanupFailure(claim, "unavailable", nextAttempt)).isTrue();
        assertThat(variant.getCleanupNextAttemptAt()).isEqualTo(nextAttempt);
        assertThat(variant.getCleanupRetryCount()).isOne();
    }

    @Test
    void delayedClaimPreservesRetryBackoffAndLeavesStorageUntouched() {
        FileVariant variant = variant(FileStorageStatus.PENDING_DELETE);
        LocalDateTime nextAttempt = LocalDateTime.of(2026, 11, 20, 10, 0);
        variant.recordCleanupFailure("unavailable", nextAttempt);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));
        var stale = new FileVariantStateCommand.FileVariantCleanupSnapshot(3L, 0);

        assertThat(command.deleteClaimed(stale)).isFalse();
        assertThat(command.recordCleanupFailure(stale, "outdated failure", nextAttempt.plusMinutes(30))).isFalse();

        assertThat(variant.getCleanupRetryCount()).isOne();
        assertThat(variant.getCleanupNextAttemptAt()).isEqualTo(nextAttempt);
        verifyNoInteractions(storage);
    }

    private FileVariant variant(FileStorageStatus status) {
        File original = File.builder().storageStatus(FileStorageStatus.ACTIVE).build();
        ReflectionTestUtils.setField(original, "fileId", 1L);
        FileVariant variant = FileVariant.builder()
                .file(original)
                .variantType(FileVariantType.THUMBNAIL)
                .filePath("variants/1/thumbnail.webp")
                .fileSize(10L)
                .mimeType("image/webp")
                .width(10)
                .height(10)
                .storageStatus(status)
                .build();
        ReflectionTestUtils.setField(variant, "fileVariantId", 3L);
        return variant;
    }
}
