package com.weedrice.whiteboard.domain.file.dto;

import com.weedrice.whiteboard.domain.file.entity.File;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResponse {
    private Long fileId;
    private String originalName;
    private String storedName; // filePath
    private String fileUrl; // 실제 접근 가능한 URL (여기서는 filePath를 그대로 사용)
    private Long fileSize;
    private String mimeType;

    public static FileUploadResponse from(File file) {
        // TODO: 실제 fileUrl은 CDN 또는 파일 서버 URL로 변경 필요
        String fileUrl = "/uploads/" + file.getFilePath(); // 임시 URL

        return FileUploadResponse.builder()
                .fileId(file.getFileId())
                .originalName(file.getOriginalName())
                .storedName(file.getFilePath())
                .fileUrl(fileUrl)
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .build();
    }
}
