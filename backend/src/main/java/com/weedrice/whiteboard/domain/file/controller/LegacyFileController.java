package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.service.FileDownloadService;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
class LegacyFileController {

    private final FileDownloadService fileDownloadService;

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            @CurrentUserId(required = false) Long viewerUserId) {
        FileDownloadResponse download = fileDownloadService.downloadFile(fileId, viewerUserId);

        return FileDownloadResponseAssembler.toResponse(download);
    }
}
