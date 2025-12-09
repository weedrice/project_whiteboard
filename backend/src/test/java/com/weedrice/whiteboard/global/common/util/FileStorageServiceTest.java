package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private FileStorageService fileStorageService;

    private String bucket = "test-bucket";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileStorageService, "bucket", bucket);
    }

    @Test
    @DisplayName("파일 저장 성공")
    void storeFile_success() {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);

        // when
        String fileName = fileStorageService.storeFile(file);

        // then
        assertThat(fileName).isNotNull();
        assertThat(fileName).endsWith(".txt");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("파일 저장 실패 - S3 오류")
    void storeFile_failure() {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        // Simulating SdkClientException or general Exception during S3 putObject
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new RuntimeException("S3 Error"));

        // when & then
        assertThatThrownBy(() -> fileStorageService.storeFile(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("파일 로드 성공")
    void loadFile_success() throws IOException {
        // given
        String fileName = "test.txt";
        // Mock a ResponseInputStream that returns a valid stream
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> mockResponseStream = mock(ResponseInputStream.class);
        when(mockResponseStream.read()).thenReturn(1, 2, -1); // Return some bytes then EOF
        
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockResponseStream);

        // when
        InputStream resultStream = fileStorageService.loadFile(fileName);

        // then
        assertThat(resultStream).isNotNull();
        verify(s3Client).getObject(any(GetObjectRequest.class));
        // Additional check: ensure stream can be read (at least one byte)
        assertThat(resultStream.read()).isNotEqualTo(-1);
    }

    @Test
    @DisplayName("파일 로드 실패")
    void loadFile_failure() {
        // given
        String fileName = "nonexistent.txt";
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(new RuntimeException("Not Found"));

        // when & then
        assertThatThrownBy(() -> fileStorageService.loadFile(fileName))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("파일 삭제 성공")
    void deleteFile_success() {
        // given
        String fileName = "test.txt";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(null);

        // when
        fileStorageService.deleteFile(fileName);

        // then
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("파일 삭제 실패 - S3 오류 로깅 확인")
    void deleteFile_failureLogsError() {
        // given
        String fileName = "fail.txt";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new RuntimeException("S3 Delete Error"));

        // Redirect stderr to capture output
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        try {
            // when
            fileStorageService.deleteFile(fileName);

            // then
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
            assertThat(errContent.toString()).contains("S3 파일 삭제 실패: " + fileName + ", S3 Delete Error");
        } finally {
            // Restore original stderr
            System.setErr(originalErr);
        }
    }
}