package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadStateCommandTest {

    @Mock FileRepository fileRepository;
    @Mock UserRepository userRepository;
    @Mock UserWritableResolver userWritableResolver;
    @Mock TransactionTemplate transactionTemplate;

    private FileUploadStateCommand command;

    @BeforeEach
    void setUp() {
        command = new FileUploadStateCommand(
                fileRepository, userRepository, userWritableResolver, transactionTemplate, Clock.systemUTC());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(User.builder().build()));
    }

    @Test
    void rejectsWhenTemporaryFileCountQuotaIsExhausted() {
        FileRepository.TemporaryUploadUsageProjection usage = usage(
                FileUploadStateCommand.MAX_TEMPORARY_FILE_COUNT,
                1L);
        when(fileRepository.findTemporaryUploadUsage(1L)).thenReturn(usage);

        assertThatThrownBy(() -> createPending(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(fileRepository, never()).save(any());
    }

    @Test
    void rejectsWhenTemporaryByteQuotaWouldBeExceeded() {
        FileRepository.TemporaryUploadUsageProjection usage = usage(
                1L,
                FileUploadStateCommand.MAX_TEMPORARY_FILE_BYTES);
        when(fileRepository.findTemporaryUploadUsage(1L)).thenReturn(usage);

        assertThatThrownBy(() -> createPending(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(fileRepository, never()).save(any());
    }

    private void createPending(long fileSize) {
        command.createPendingUploadRecord(
                "stored.jpg", "original.jpg", fileSize, "image/jpeg", 1, 1, 0, 1L);
    }

    private FileRepository.TemporaryUploadUsageProjection usage(long count, long bytes) {
        FileRepository.TemporaryUploadUsageProjection usage =
                mock(FileRepository.TemporaryUploadUsageProjection.class);
        when(usage.getFileCount()).thenReturn(count);
        when(usage.getTotalBytes()).thenReturn(bytes);
        return usage;
    }
}
