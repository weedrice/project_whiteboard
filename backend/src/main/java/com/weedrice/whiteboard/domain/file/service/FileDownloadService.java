package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class FileDownloadService {

    private final FileAccessService fileAccessService;
    private final FileStorageService fileStorageService;

    public FileDownloadResponse downloadFile(Long fileId, Long viewerUserId) {
        File file = fileAccessService.getFileForDownload(fileId, viewerUserId);
        InputStream inputStream = fileStorageService.loadFile(file.getFilePath());

        return new FileDownloadResponse(inputStream, file.getOriginalName(), file.getMimeType());
    }
}
