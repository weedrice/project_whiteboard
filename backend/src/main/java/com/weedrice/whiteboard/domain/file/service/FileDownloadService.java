package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
public class FileDownloadService {

    private final FileAccessService fileAccessService;
    private final FileStorageService fileStorageService;
    private final FileVariantRepository fileVariantRepository;

    public FileDownloadResponse downloadFile(Long fileId, Long viewerUserId) {
        FileAccessResult access = fileAccessService.getFileAccessForDownload(fileId, viewerUserId);
        File file = access.file();
        return toDownload(fileId, file, file.getFilePath(), file.getFileSize(), file.getMimeType(), access.cacheScope());
    }

    public FileDownloadResponse downloadVariantFile(Long fileId, FileVariantType variantType, Long viewerUserId) {
        FileAccessResult access = fileAccessService.getFileAccessForDownload(fileId, viewerUserId);
        File file = access.file();
        return fileVariantRepository.findByFileFileIdAndVariantType(fileId, variantType)
                .map(variant -> toVariantDownload(fileId, file, variant, access.cacheScope()))
                .orElseGet(() -> toDownload(
                        fileId,
                        file,
                        file.getFilePath(),
                        file.getFileSize(),
                        file.getMimeType(),
                        access.cacheScope()));
    }

    private FileDownloadResponse toVariantDownload(
            Long fileId,
            File originalFile,
            FileVariant variant,
            FileAccessResult.CacheScope cacheScope) {
        return toDownload(
                fileId,
                originalFile,
                variant.getFilePath(),
                variant.getFileSize(),
                variant.getMimeType(),
                cacheScope);
    }

    private FileDownloadResponse toDownload(
            Long fileId,
            File originalFile,
            String storagePath,
            Long fileSize,
            String mimeType,
            FileAccessResult.CacheScope cacheScope) {
        boolean freshnessCacheable = cacheScope == FileAccessResult.CacheScope.PUBLIC_EMOTICON;
        String entityTag = freshnessCacheable ? createEntityTag(fileId, storagePath, fileSize) : null;
        return new FileDownloadResponse(
                () -> fileStorageService.loadFile(storagePath),
                originalFile.getOriginalName(),
                mimeType,
                freshnessCacheable,
                entityTag);
    }

    private String createEntityTag(Long fileId, String storagePath, Long fileSize) {
        String source = fileId + ":" + storagePath + ":" + fileSize;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
