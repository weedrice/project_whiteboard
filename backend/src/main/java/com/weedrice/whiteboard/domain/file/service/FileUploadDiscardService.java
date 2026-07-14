package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileUploadDiscardResponse;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FileUploadDiscardService {

    private final FileRepository fileRepository;
    private final Clock clock;

    @Transactional
    public FileUploadDiscardResponse discardTemporaryUploads(Long uploaderId, List<Long> fileIds) {
        List<Long> uniqueFileIds = new LinkedHashSet<>(fileIds).stream()
                .filter(Objects::nonNull)
                .toList();
        if (uniqueFileIds.isEmpty()) {
            return new FileUploadDiscardResponse(0);
        }

        int discardedCount = fileRepository.requestDeletionForOwnedTemporaryFiles(
                uniqueFileIds,
                uploaderId,
                LocalDateTime.now(clock));
        return new FileUploadDiscardResponse(discardedCount);
    }
}
