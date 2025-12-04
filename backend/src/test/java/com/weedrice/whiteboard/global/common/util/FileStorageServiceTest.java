package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileStorageService, "bucket", "test-bucket");
    }

    @Test
    @DisplayName("파일 저장 성공 - S3 putObject 호출 확인")
    void storeFile_success() {
        // given
        MockMultipartFile multipartFile = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());

        // when
        fileStorageService.storeFile(multipartFile);

        // then
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("파일 삭제 성공 - S3 deleteObject 호출 확인")
    void deleteFile_success() {
        // given
        String storedFileName = "test-uuid.txt";

        // when
        fileStorageService.deleteFile(storedFileName);

        // then
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
