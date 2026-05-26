package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.service.FileDownloadService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.optionalUserId;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
class LegacyFileController {

    private final FileDownloadService fileDownloadService;

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long viewerUserId = optionalUserId(userDetails);
        FileDownloadResponse download = fileDownloadService.downloadFile(fileId, viewerUserId);

        return FileDownloadResponseAssembler.toResponse(download);
    }
}
