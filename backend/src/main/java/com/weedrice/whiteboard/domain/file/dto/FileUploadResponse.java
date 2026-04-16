package com.weedrice.whiteboard.domain.file.dto;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResponse {
    private Long fileId;
    private String originalName;
    private String storedName;
    private String fileUrl;
    private Long fileSize;
    private String mimeType;

    public static FileUploadResponse from(File file) {
        return FileUploadResponse.builder()
                .fileId(file.getFileId())
                .originalName(file.getOriginalName())
                .storedName(file.getFilePath())
                .fileUrl(FileUrlResolver.resolve(file.getFileId()))
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .build();
    }
}
