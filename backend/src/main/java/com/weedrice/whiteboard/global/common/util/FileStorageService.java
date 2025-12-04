package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String storeFile(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID().toString() + fileExtension;

        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            RequestBody requestBody = RequestBody.fromInputStream(file.getInputStream(), file.getSize());
            s3Client.putObject(putOb, requestBody);

            return fileName; // 저장된 파일명(S3 Key) 반환
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 파일 업로드 실패: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 서비스 오류: " + ex.getMessage());
        }
    }

    public InputStream loadFile(String fileName) {
        try {
            GetObjectRequest getOb = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();
            return s3Client.getObject(getOb);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "S3 파일 로드 실패: " + fileName);
        }
    }

    public void deleteFile(String fileName) {
        try {
            DeleteObjectRequest deleteOb = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();
            s3Client.deleteObject(deleteOb);
        } catch (Exception ex) {
            // 파일 삭제 실패는 치명적이지 않으므로 로그만 남김
            System.err.println("S3 파일 삭제 실패: " + fileName + ", " + ex.getMessage());
        }
    }
}