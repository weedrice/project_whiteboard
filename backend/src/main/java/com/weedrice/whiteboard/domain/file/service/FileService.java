package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public File uploadFile(Long uploaderId, MultipartFile multipartFile) {
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 파일 유효성 검사 (크기, 형식 등)
        if (multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "업로드할 파일이 없습니다.");
        }
        if (multipartFile.getSize() > 10 * 1024 * 1024) { // 10MB 제한
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        // TODO: 허용되는 파일 타입 검사 로직 추가

        String storedFileName = fileStorageService.storeFile(multipartFile);
        String originalFileName = multipartFile.getOriginalFilename();
        Long fileSize = multipartFile.getSize();
        String mimeType = multipartFile.getContentType();

        File file = File.builder()
                .filePath(storedFileName)
                .originalName(originalFileName)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .uploader(uploader)
                .build(); // related_id, related_type은 null로 시작 (임시 파일)

        return fileRepository.save(file);
    }

    @Transactional
    public void associateFileWithEntity(Long fileId, Long relatedId, String relatedType) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        file.updateRelatedInfo(relatedId, relatedType);
        fileRepository.save(file);
    }

    @Transactional
    public void cleanUpTemporaryFiles() {
        // 24시간이 지난 미연결 임시 파일 삭제
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<File> temporaryFiles = fileRepository.findByRelatedIdIsNullAndCreatedAtBefore(twentyFourHoursAgo);

        for (File file : temporaryFiles) {
            fileStorageService.deleteFile(file.getFilePath()); // 실제 파일 삭제
            fileRepository.delete(file); // DB 레코드 삭제
        }
    }

    public File getFile(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
