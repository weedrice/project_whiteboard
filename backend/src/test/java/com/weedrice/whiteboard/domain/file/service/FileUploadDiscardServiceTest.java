package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileUploadDiscardResponse;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadDiscardServiceTest {

    @Mock
    private FileRepository fileRepository;

    private FileUploadDiscardService fileUploadDiscardService;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T01:00:00Z"), ZoneOffset.UTC);
        now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        fileUploadDiscardService = new FileUploadDiscardService(fileRepository, clock);
    }

    @Test
    @DisplayName("파일 ID 중복과 null을 제거하고 현재 사용자 임시 파일의 폐기를 요청한다")
    void discardTemporaryUploads_deduplicatesFileIds() {
        when(fileRepository.requestDeletionForOwnedTemporaryFiles(List.of(3L, 4L), 1L, now)).thenReturn(2);

        FileUploadDiscardResponse response = fileUploadDiscardService.discardTemporaryUploads(
                1L,
                Arrays.asList(3L, 3L, null, 4L));

        assertThat(response.discardedCount()).isEqualTo(2);
        verify(fileRepository).requestDeletionForOwnedTemporaryFiles(List.of(3L, 4L), 1L, now);
    }

    @Test
    @DisplayName("유효한 파일 ID가 없으면 저장소를 호출하지 않는다")
    void discardTemporaryUploads_ignoresNullOnlyList() {
        FileUploadDiscardResponse response = fileUploadDiscardService.discardTemporaryUploads(
                1L,
                Arrays.asList(null, null));

        assertThat(response.discardedCount()).isZero();
        verify(fileRepository, never()).requestDeletionForOwnedTemporaryFiles(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }
}
