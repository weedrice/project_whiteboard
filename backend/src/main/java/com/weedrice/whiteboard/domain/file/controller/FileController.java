package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.service.FileDownloadService;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final FileDownloadService fileDownloadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile multipartFile,
            @CurrentUserId Long userId) {
        return ApiResponse.success(fileService.uploadFile(userId, multipartFile));
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileSimpleResponse> uploadSimple(
            @RequestParam("file") MultipartFile multipartFile,
            @CurrentUserId Long userId) {
        return ApiResponse.success(fileService.uploadSimpleFile(userId, multipartFile));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            @CurrentUserId(required = false) Long viewerUserId) {
        FileDownloadResponse download = fileDownloadService.downloadFile(fileId, viewerUserId);

        return FileDownloadResponseAssembler.toResponse(download);
    }

    @GetMapping("/{fileId}/variants/{variantType}")
    public ResponseEntity<Resource> downloadVariantFile(
            @PathVariable Long fileId,
            @PathVariable String variantType,
            @CurrentUserId(required = false) Long viewerUserId) {
        FileDownloadResponse download = fileDownloadService.downloadVariantFile(
                fileId,
                FileVariantType.fromPathSegment(variantType),
                viewerUserId);

        return FileDownloadResponseAssembler.toResponse(download);
    }
}
