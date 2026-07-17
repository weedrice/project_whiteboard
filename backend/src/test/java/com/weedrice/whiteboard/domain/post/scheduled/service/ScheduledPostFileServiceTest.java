package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPostFile;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostFileRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledPostFileServiceTest {

    @Mock FileRepository fileRepository;
    @Mock ScheduledPostFileRepository scheduledPostFileRepository;

    private ScheduledPostFileService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ScheduledPostFileService(fileRepository, scheduledPostFileRepository);
        owner = User.builder().loginId("owner").email("owner@example.com").displayName("owner").build();
        ReflectionTestUtils.setField(owner, "userId", 1L);
    }

    @Test
    void replaceReferencesLocksInIdOrderAndPreservesRequestedDisplayOrder() {
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file(10L)));
        when(fileRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(file(20L)));

        service.replaceReferences(7L, 1L, null, List.of(20L, 10L));

        verify(fileRepository).findByIdForUpdate(10L);
        verify(fileRepository).findByIdForUpdate(20L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ScheduledPostFile>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(scheduledPostFileRepository).saveAll(captor.capture());
        List<ScheduledPostFile> references = ((List<ScheduledPostFile>) captor.getValue());
        assertThat(references).extracting(reference -> reference.getId().getFileId())
                .containsExactly(20L, 10L);
        assertThat(references).extracting(ScheduledPostFile::getFileOrder)
                .containsExactly(1, 2);
    }

    @Test
    void replaceReferencesRejectsFileReservedByAnotherScheduledPost() {
        when(fileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(file(10L)));
        when(scheduledPostFileRepository.existsByIdFileIdAndIdScheduledPostIdNot(10L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.replaceReferences(7L, 1L, null, List.of(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_ALREADY_ASSOCIATED);

        verify(scheduledPostFileRepository, never()).deleteAllByScheduledPostId(any());
    }

    @Test
    void replaceReferencesRejectsDuplicateFileIds() {
        assertThatThrownBy(() -> service.replaceReferences(7L, 1L, null, List.of(10L, 10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(fileRepository, never()).findByIdForUpdate(any());
    }

    private File file(Long fileId) {
        File file = File.builder()
                .filePath("test/" + fileId)
                .originalName("test.png")
                .fileSize(1L)
                .mimeType("image/png")
                .uploader(owner)
                .storageStatus(FileStorageStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(file, "fileId", fileId);
        return file;
    }
}
