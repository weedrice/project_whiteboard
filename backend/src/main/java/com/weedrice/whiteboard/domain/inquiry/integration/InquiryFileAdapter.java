package com.weedrice.whiteboard.domain.inquiry.integration;

import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryFilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
class InquiryFileAdapter implements InquiryFilePort {
    private final FileService fileService;

    @Override
    public void associateMessageFiles(List<Long> fileIds, Long ownerUserId, Long messageId, int maxFileCount) {
        fileService.associateFilesWithEntity(fileIds, ownerUserId, messageId,
                FileService.RELATED_TYPE_INQUIRY_MESSAGE, maxFileCount);
    }

    @Override
    public List<MessageFile> findMessageFiles(Collection<Long> messageIds) {
        return fileService.getFilesByRelatedEntityIn(
                        messageIds.stream().toList(), FileService.RELATED_TYPE_INQUIRY_MESSAGE).stream()
                .map(file -> new MessageFile(
                        file.getFileId(), file.getRelatedId(), file.getOriginalName(), file.getFileSize(),
                        file.getMimeType(), FileUrlResolver.resolve(file.getFileId())))
                .toList();
    }
}
