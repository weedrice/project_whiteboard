package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class FileDownloadService {

    private final FileAccessService fileAccessService;
    private final FileStorageService fileStorageService;
    private final FileVariantRepository fileVariantRepository;

    public FileDownloadResponse downloadFile(Long fileId, Long viewerUserId) {
        File file = fileAccessService.getFileForDownload(fileId, viewerUserId);
        InputStream inputStream = fileStorageService.loadFile(file.getFilePath());

        return new FileDownloadResponse(inputStream, file.getOriginalName(), file.getMimeType());
    }

    public FileDownloadResponse downloadVariantFile(Long fileId, FileVariantType variantType, Long viewerUserId) {
        File file = fileAccessService.getFileForDownload(fileId, viewerUserId);
        return fileVariantRepository.findByFileFileIdAndVariantType(fileId, variantType)
                .map(variant -> toVariantDownload(file, variant))
                .orElseGet(() -> {
                    InputStream inputStream = fileStorageService.loadFile(file.getFilePath());
                    return new FileDownloadResponse(inputStream, file.getOriginalName(), file.getMimeType());
                });
    }

    private FileDownloadResponse toVariantDownload(File originalFile, FileVariant variant) {
        InputStream inputStream = fileStorageService.loadFile(variant.getFilePath());
        return new FileDownloadResponse(inputStream, originalFile.getOriginalName(), variant.getMimeType());
    }
}
