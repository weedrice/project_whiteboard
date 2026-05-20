package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String storeFile(MultipartFile file, String validatedMimeType) {
        String fileName = generateStoredFileName(file.getOriginalFilename());
        storeFileAs(file, validatedMimeType, fileName);
        return fileName;
    }

    public String generateStoredFileName(String originalFileName) {
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return UUID.randomUUID() + fileExtension;
    }

    public void storeFileAs(MultipartFile file, String validatedMimeType, String fileName) {
        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(validatedMimeType)
                    .build();

            RequestBody requestBody = RequestBody.fromInputStream(file.getInputStream(), file.getSize());
            s3Client.putObject(putOb, requestBody);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 file upload failed: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 service error: " + ex.getMessage());
        }
    }

    public InputStream loadFile(String fileName) {
        try {
            GetObjectRequest getOb = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();
            return s3Client.getObject(getOb);
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "S3 file load failed: " + fileName);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "S3 file load failed: " + fileName);
            }
            throw new BusinessException(ErrorCode.FILE_LOAD_ERROR, "S3 file load failed: " + fileName);
        } catch (SdkClientException ex) {
            throw new BusinessException(ErrorCode.FILE_LOAD_ERROR, "S3 file load failed: " + fileName);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_LOAD_ERROR, "S3 file load failed: " + fileName);
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
            log.warn("Failed to delete file quietly from storage. fileName={}", fileName, ex);
        }
    }

    public void deleteFileOrThrow(String fileName) {
        try {
            DeleteObjectRequest deleteOb = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();
            s3Client.deleteObject(deleteOb);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_DELETE_ERROR,
                    "S3 file delete failed: " + fileName + ", " + ex.getMessage());
        }
    }
}
