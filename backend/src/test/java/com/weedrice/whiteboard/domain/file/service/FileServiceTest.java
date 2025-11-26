package com.weedrice.whiteboard.domain.file.service;

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
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private FileService fileService;

    @Test
    @DisplayName("파일 업로드 성공")
    void uploadFile_success() {
        // given
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        MultipartFile multipartFile = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
        File file = File.builder()
                .filePath("storedFileName.txt")
                .originalName("test.txt")
                .fileSize(10L)
                .mimeType("text/plain")
                .uploader(uploader)
                .build();

        when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
        when(fileStorageService.storeFile(multipartFile)).thenReturn("storedFileName.txt");
        when(fileRepository.save(any(File.class))).thenReturn(file);

        // when
        File uploadedFile = fileService.uploadFile(uploaderId, multipartFile);

        // then
        assertThat(uploadedFile.getOriginalName()).isEqualTo("test.txt");
        assertThat(uploadedFile.getFilePath()).isEqualTo("storedFileName.txt");
    }
}
