package com.weedrice.whiteboard.domain.file.dto;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileSimpleResponse {
    private String url;
    private Long fileId;

    public static FileSimpleResponse from(File file) {
        return FileSimpleResponse.builder()
                .url(FileUrlResolver.resolve(file.getFileId()))
                .fileId(file.getFileId())
                .build();
    }
}
