package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
class EmoticonFileReferenceService {

    private final EmoticonImageRepository emoticonImageRepository;
    private final EmoticonMasterRepository emoticonMasterRepository;

    boolean isReferenced(Long fileId) {
        if (fileId == null) {
            return false;
        }
        List<String> candidateUrls = FileUrlResolver.referenceCandidates(fileId);
        return emoticonImageRepository.existsByImageUrlIn(candidateUrls)
                || emoticonMasterRepository.existsByThumbnailUrlIn(candidateUrls);
    }

    List<Long> excludeReferencedFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }

        Map<String, Long> fileIdsByUrl = mapFileIdsByReferenceUrl(fileIds);
        Set<Long> referencedFileIds = findReferencedFileIds(fileIdsByUrl);

        return fileIds.stream()
                .filter(fileId -> !referencedFileIds.contains(fileId))
                .toList();
    }

    private Map<String, Long> mapFileIdsByReferenceUrl(List<Long> fileIds) {
        Map<String, Long> fileIdsByUrl = new LinkedHashMap<>();
        for (Long fileId : fileIds) {
            for (String candidateUrl : FileUrlResolver.referenceCandidates(fileId)) {
                fileIdsByUrl.put(candidateUrl, fileId);
            }
        }
        return fileIdsByUrl;
    }

    private Set<Long> findReferencedFileIds(Map<String, Long> fileIdsByUrl) {
        Set<Long> referencedFileIds = new LinkedHashSet<>();
        List<String> candidateUrls = fileIdsByUrl.keySet().stream().toList();

        List<String> imageUrls = emoticonImageRepository.findReferencedImageUrls(candidateUrls);
        if (imageUrls != null) {
            imageUrls.forEach(url -> referencedFileIds.add(fileIdsByUrl.get(url)));
        }
        List<String> thumbnailUrls = emoticonMasterRepository.findReferencedThumbnailUrls(candidateUrls);
        if (thumbnailUrls != null) {
            thumbnailUrls.forEach(url -> referencedFileIds.add(fileIdsByUrl.get(url)));
        }

        return referencedFileIds;
    }
}
