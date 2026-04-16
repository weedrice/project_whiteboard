package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @InjectMocks
    private FileDeletionWorker fileDeletionWorker;

    @Test
    @DisplayName("스토리지 삭제 성공 시 파일 레코드를 최종 삭제한다")
    void processDeletion_deletesFileAfterStorageSuccess() {
        File file = File.builder()
                .filePath("stored.jpg")
                .originalName("stored.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(com.weedrice.whiteboard.domain.user.entity.User.builder().build())
                .storageStatus(FileStorageStatus.PENDING_DELETE)
                .build();

        when(fileRepository.findById(10L)).thenReturn(Optional.of(file));
        doAnswer(invocation -> {
            Consumer<Object> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        fileDeletionWorker.processDeletion(10L);

        verify(fileStorageService).deleteFileOrThrow("stored.jpg");
        verify(fileRepository).delete(file);
    }

    @Test
    @DisplayName("스토리지 삭제 실패 시 파일 상태를 DELETE_FAILED로 전환한다")
    void processDeletion_marksFileFailedWhenStorageDeleteFails() {
        File file = File.builder()
                .filePath("stored.jpg")
                .originalName("stored.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(com.weedrice.whiteboard.domain.user.entity.User.builder().build())
                .storageStatus(FileStorageStatus.PENDING_DELETE)
                .build();

        when(fileRepository.findById(10L)).thenReturn(Optional.of(file));
        doAnswer(invocation -> {
            Consumer<Object> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        doThrow(new BusinessException(ErrorCode.FILE_DELETE_ERROR))
                .when(fileStorageService).deleteFileOrThrow(eq("stored.jpg"));

        fileDeletionWorker.processDeletion(10L);

        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.DELETE_FAILED);
        assertThat(file.getDeleteRetryCount()).isEqualTo(1);
        verify(fileRepository, never()).delete(any());
    }
}
