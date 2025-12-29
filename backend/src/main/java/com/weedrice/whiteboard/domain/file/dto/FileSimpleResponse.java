package com.weedrice.whiteboard.domain.file.dto;

import com.weedrice.whiteboard.domain.file.entity.File;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileSimpleResponse {
    private String url;
    private Long fileId;

    public static FileSimpleResponse from(File file) {
        return FileSimpleResponse.builder()
                .url("/api/v1/files/" + file.getFileId())
                .fileId(file.getFileId())
                .build();
    }
}
