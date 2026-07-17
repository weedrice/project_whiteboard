package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileVariantStateCommandTest {

    @Mock
    private FileVariantRepository repository;

    @Test
    void activate_changesOnlyPendingIntentToActive() {
        FileVariant variant = variant(FileStorageStatus.PENDING_UPLOAD);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));
        FileVariantStateCommand command = new FileVariantStateCommand(repository);

        command.activate(3L);

        assertThat(variant.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
    }

    @Test
    void claimCleanup_marksStalePendingIntentBeforeExternalDelete() {
        FileVariant variant = variant(FileStorageStatus.PENDING_UPLOAD);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 17, 0, 0);
        ReflectionTestUtils.setField(variant, "createdAt", createdAt);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));
        FileVariantStateCommand command = new FileVariantStateCommand(repository);

        FileVariantStateCommand.FileVariantCleanupSnapshot snapshot = command.claimCleanup(
                3L,
                createdAt.plusHours(2));

        assertThat(snapshot.filePath()).isEqualTo("variants/1/thumbnail.webp");
        assertThat(variant.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
    }

    @Test
    void deleteClaimed_removesIntentOnlyAfterStorageDeleteSucceeded() {
        FileVariant variant = variant(FileStorageStatus.PENDING_DELETE);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(variant));
        FileVariantStateCommand command = new FileVariantStateCommand(repository);

        command.deleteClaimed(3L);

        verify(repository).delete(variant);
    }

    private FileVariant variant(FileStorageStatus status) {
        FileVariant variant = FileVariant.builder()
                .file(File.builder().build())
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
