package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    public String storeFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath); // 디렉토리가 없으면 생성

            String originalFileName = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + fileExtension;
            Path targetLocation = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation);

            return fileName; // 저장된 파일명 반환
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다: " + ex.getMessage());
        }
    }

    public Path loadFile(String fileName) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        return uploadPath.resolve(fileName).normalize();
    }

    public void deleteFile(String fileName) {
        try {
            Path filePath = loadFile(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            // 파일 삭제 실패는 치명적이지 않으므로 로그만 남김
            System.err.println("파일 삭제 실패: " + fileName + ", " + ex.getMessage());
        }
    }
}
