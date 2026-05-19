package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;

import java.util.List;

class EmoticonAttachmentHelper {

    private final FileService fileService;

    EmoticonAttachmentHelper(FileService fileService) {
        this.fileService = fileService;
    }

    String attachFile(Long fileId, Long userId, Long emoticonId, String relatedType) {
        fileService.associateFileWithEntity(fileId, userId, emoticonId, relatedType);
        return FileUrlResolver.resolve(fileId);
    }

    List<String> attachFiles(List<Long> fileIds, Long userId, Long emoticonId, String relatedType) {
        return fileService.associateFilesWithEntity(fileIds, userId, emoticonId, relatedType);
    }

    void deleteAssociatedFile(String fileUrl, Long emoticonId, String relatedType) {
        Long fileId = FileService.extractFileIdFromUrl(fileUrl);
        if (fileId != null) {
            fileService.deleteFileWithStorageIfAssociated(fileId, emoticonId, relatedType);
        }
    }

    void deleteAssociatedFileIfDifferent(String fileUrl, Long selectedFileId, Long emoticonId, String relatedType) {
        Long fileId = FileService.extractFileIdFromUrl(fileUrl);
        if (fileId != null && !fileId.equals(selectedFileId)) {
            fileService.deleteFileWithStorageIfAssociated(fileId, emoticonId, relatedType);
        }
    }
}
