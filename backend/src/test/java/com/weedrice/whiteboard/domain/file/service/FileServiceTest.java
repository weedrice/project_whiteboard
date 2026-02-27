package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private FileService fileService;

    @Test
    @DisplayName("파일 업로드 성공")
    void uploadFile_success() {
        // given
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        MultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(10L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();

        when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
        when(fileStorageService.storeFile(multipartFile)).thenReturn("storedFileName.jpg");
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<File> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(fileRepository.save(any(File.class))).thenReturn(file);

        // when
        FileUploadResponse uploadedFile = fileService.uploadFile(uploaderId, multipartFile);

        // then
        assertThat(uploadedFile.getOriginalName()).isEqualTo("test.jpg");
        assertThat(uploadedFile.getStoredName()).isEqualTo("storedFileName.jpg");
    }

    @Test
    @DisplayName("SVG 파일 업로드 차단")
    void uploadFile_rejectSvg() {
        // given
        Long uploaderId = 1L;
        MultipartFile multipartFile = new MockMultipartFile("file", "xss.svg", "image/svg+xml",
                "<svg><script>alert(1)</script></svg>".getBytes());

        // when & then
        assertThatThrownBy(() -> fileService.uploadFile(uploaderId, multipartFile))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        com.weedrice.whiteboard.global.exception.ErrorCode.INVALID_FILE_TYPE);
    }
}
