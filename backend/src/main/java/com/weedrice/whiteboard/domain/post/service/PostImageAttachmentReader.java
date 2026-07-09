package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PostImageAttachmentReader {

    private final FileService fileService;

    public List<String> getImageUrls(@NonNull Long postId) {
        return fileService.getFilesByRelatedEntity(postId, FileService.RELATED_TYPE_POST_CONTENT).stream()
                .filter(this::isImage)
                .map(file -> FileUrlResolver.resolveMedium(file.getFileId()))
                .toList();
    }

    public Set<Long> getPostIdsWithImages(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(fileService.getRelatedIdsWithImages(postIds, FileService.RELATED_TYPE_POST_CONTENT));
    }

    private boolean isImage(File file) {
        String mimeType = file.getMimeType();
        return mimeType != null && mimeType.startsWith("image/");
    }
}
