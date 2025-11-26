package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @InjectMocks
    private FileStorageService fileStorageService;

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("test-upload-dir");
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    @Test
    @DisplayName("파일 저장 성공")
    void storeFile_success() throws IOException {
        // given
        MockMultipartFile multipartFile = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());

        // when
        String storedFileName = fileStorageService.storeFile(multipartFile);

        // then
        Path storedFile = tempDir.resolve(storedFileName);
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.readString(storedFile)).isEqualTo("test data");
    }

    @Test
    @DisplayName("파일 삭제 성공")
    void deleteFile_success() throws IOException {
        // given
        MockMultipartFile multipartFile = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
        String storedFileName = fileStorageService.storeFile(multipartFile);
        Path storedFile = tempDir.resolve(storedFileName);
        assertThat(Files.exists(storedFile)).isTrue();

        // when
        fileStorageService.deleteFile(storedFileName);

        // then
        assertThat(Files.exists(storedFile)).isFalse();
    }
}
