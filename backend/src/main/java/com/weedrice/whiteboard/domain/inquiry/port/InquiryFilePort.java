package com.weedrice.whiteboard.domain.inquiry.port;

import java.util.Collection;
import java.util.List;

public interface InquiryFilePort {
    void associateMessageFiles(List<Long> fileIds, Long ownerUserId, Long messageId, int maxFileCount);

    List<MessageFile> findMessageFiles(Collection<Long> messageIds);

    record MessageFile(
            Long fileId,
            Long messageId,
            String originalName,
            Long fileSize,
            String mimeType,
            String url) {
    }
}
