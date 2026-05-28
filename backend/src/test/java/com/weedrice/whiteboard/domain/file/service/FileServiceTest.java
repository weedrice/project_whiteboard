package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileRepository.FirstImageFileIdProjection;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserWritableResolver userWritableResolver;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private FileTemporaryCleanupWorker fileTemporaryCleanupWorker;
    @Mock
    private EntityManager entityManager;

    private FileService fileService;

    @BeforeEach
    void setUpFileService() {
        FileUploadService fileUploadService = new FileUploadService(
                fileRepository,
                userWritableResolver,
                fileStorageService,
                transactionTemplate);
        FileAssociationService fileAssociationService = new FileAssociationService(
                fileRepository,
                userRepository,
                boardRepository);
        ReflectionTestUtils.setField(fileAssociationService, "entityManager", entityManager);
        fileService = new FileService(
                fileUploadService,
                fileAssociationService,
                fileRepository,
                fileTemporaryCleanupWorker);
    }

    @Test
    @DisplayName("파일 업로드 성공")
    void uploadFile_success() {
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegHeader);
        when(userWritableResolver.resolve(uploaderId)).thenReturn(uploader);
        stubSuccessfulUpload(multipartFile, "test.jpg", "image/jpeg", "storedFileName.jpg");

        FileUploadResponse uploadedFile = fileService.uploadFile(uploaderId, multipartFile);

        assertThat(uploadedFile.getOriginalName()).isEqualTo("test.jpg");
        assertThat(uploadedFile.getStoredName()).isEqualTo("storedFileName.jpg");
        assertThat(uploadedFile.getFileUrl()).isEqualTo("/api/v1/files/10");
        assertThat(uploadedFile.getMimeType()).isEqualTo("image/jpeg");
        verify(fileStorageService).storeFileAs(multipartFile, "image/jpeg", "storedFileName.jpg");
    }

    @Test
    @DisplayName("image/jpg 선언 JPEG 파일은 image/jpeg로 정규화해 저장한다")
    void uploadFile_jpgAliasStoresDetectedMimeType() {
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/jpg", jpegHeader);
        when(userWritableResolver.resolve(uploaderId)).thenReturn(uploader);
        stubSuccessfulUpload(multipartFile, "test.jpg", "image/jpeg", "storedFileName.jpg");

        FileUploadResponse uploadedFile = fileService.uploadFile(uploaderId, multipartFile);

        assertThat(uploadedFile.getMimeType()).isEqualTo("image/jpeg");
        verify(fileStorageService).storeFileAs(multipartFile, "image/jpeg", "storedFileName.jpg");
    }

    @Test
    @DisplayName("이미지 MIME 판별은 헤더 스트림만 읽는다")
    void uploadFile_detectsImageMimeTypeFromHeaderStream() throws Exception {
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        byte[] pngHeader = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x48, 0x44, 0x52
        };
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(10L);
        when(multipartFile.getContentType()).thenReturn("image/png");
        when(multipartFile.getOriginalFilename()).thenReturn("test.png");
        when(multipartFile.getInputStream()).thenReturn(new HeaderLimitedInputStream(pngHeader, 12));
        when(userWritableResolver.resolve(uploaderId)).thenReturn(uploader);
        stubSuccessfulUpload(multipartFile, "test.png", "image/png", "storedFileName.png");

        FileUploadResponse uploadedFile = fileService.uploadFile(uploaderId, multipartFile);

        assertThat(uploadedFile.getMimeType()).isEqualTo("image/png");
        verify(multipartFile).getInputStream();
        verify(multipartFile, never()).getBytes();
        verify(fileStorageService).storeFileAs(multipartFile, "image/png", "storedFileName.png");
    }

    @Test
    @DisplayName("선언 MIME과 감지 MIME이 다르면 스토리지 저장 전에 거절한다")
    void uploadFile_declaredMimeMismatch_rejectedBeforeStorage() {
        Long uploaderId = 1L;
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/png", jpegHeader);

        assertThatThrownBy(() -> fileService.uploadFile(uploaderId, multipartFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);

        verify(userWritableResolver, never()).resolve(any());
        verify(fileStorageService, never()).storeFileAs(any(), any(), any());
    }

    @Test
    @DisplayName("원본 파일명이 255자를 초과하면 스토리지 저장 전에 거절한다")
    void uploadFile_longOriginalFilename_rejectedBeforeStorage() {
        Long uploaderId = 1L;
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        String longFilename = "a".repeat(252) + ".jpg";
        MultipartFile multipartFile = new MockMultipartFile("file", longFilename, "image/jpeg", jpegHeader);

        assertThatThrownBy(() -> fileService.uploadFile(uploaderId, multipartFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR)
                .satisfies(exception -> assertThat(exception.getMessage())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR.getMessage()));

        verify(userWritableResolver, never()).resolve(any());
        verify(fileStorageService, never()).storeFileAs(any(), any(), any());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("빈 원본 파일명은 스토리지 저장 전에 거절한다")
    void uploadFile_blankOriginalFilename_rejectedBeforeStorage() {
        Long uploaderId = 1L;
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "   ", "image/jpeg", jpegHeader);

        assertThatThrownBy(() -> fileService.uploadFile(uploaderId, multipartFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR)
                .satisfies(exception -> assertThat(exception.getMessage())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR.getMessage()));

        verify(userWritableResolver, never()).resolve(any());
        verify(fileStorageService, never()).storeFileAs(any(), any(), any());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("원본 파일명은 255자까지 허용한다")
    void uploadFile_maxLengthOriginalFilename_success() {
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        String maxLengthFilename = "a".repeat(251) + ".jpg";
        MultipartFile multipartFile = new MockMultipartFile("file", maxLengthFilename, "image/jpeg", jpegHeader);

        when(userWritableResolver.resolve(uploaderId)).thenReturn(uploader);
        stubSuccessfulUpload(multipartFile, maxLengthFilename, "image/jpeg", "storedFileName.jpg");

        FileUploadResponse uploadedFile = fileService.uploadFile(uploaderId, multipartFile);

        assertThat(uploadedFile.getOriginalName()).isEqualTo(maxLengthFilename);
        verify(fileStorageService).storeFileAs(multipartFile, "image/jpeg", "storedFileName.jpg");
    }

    @Test
    @DisplayName("원본 파일명 경로 성분은 마지막 파일명으로 정규화해 저장한다")
    void uploadFile_pathOriginalFilename_savesOnlyFilename() {
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "../avatar.jpg", "image/jpeg", jpegHeader);

        when(userWritableResolver.resolve(uploaderId)).thenReturn(uploader);
        stubSuccessfulUpload(multipartFile, "avatar.jpg", "image/jpeg", "storedFileName.jpg");

        fileService.uploadFile(uploaderId, multipartFile);

        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalName()).isEqualTo("avatar.jpg");
    }

    @Test
    @DisplayName("스토리지 저장 실패 시 생성된 업로드 후보를 삭제 대기 상태로 둔다")
    void uploadFile_storageFailureMarksPendingUploadForDeletion() {
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegHeader);
        File[] savedFile = new File[1];

        when(userWritableResolver.resolve(uploaderId)).thenReturn(uploader);
        when(fileStorageService.generateStoredFileName("test.jpg")).thenReturn("storedFileName.jpg");
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<File> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback =
                    invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            ReflectionTestUtils.setField(file, "fileId", 10L);
            savedFile[0] = file;
            return file;
        });
        when(fileRepository.findByIdForUpdate(10L)).thenAnswer(invocation -> Optional.of(savedFile[0]));
        doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR))
                .when(fileStorageService)
                .storeFileAs(multipartFile, "image/jpeg", "storedFileName.jpg");

        assertThatThrownBy(() -> fileService.uploadFile(uploaderId, multipartFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(savedFile[0].getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(savedFile[0].getDeleteRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("업로드 후보가 삭제 대기 상태로 바뀌면 완료 처리하지 않는다")
    void uploadFile_doesNotCompletePendingUploadAlreadyClaimedForDeletion() {
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegHeader);
        File[] savedFile = new File[1];

        when(userWritableResolver.resolve(uploaderId)).thenReturn(uploader);
        when(fileStorageService.generateStoredFileName("test.jpg")).thenReturn("storedFileName.jpg");
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<File> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback =
                    invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            ReflectionTestUtils.setField(file, "fileId", 10L);
            savedFile[0] = file;
            return file;
        });
        when(fileRepository.findByIdForUpdate(10L)).thenAnswer(invocation -> Optional.of(savedFile[0]));
        doAnswer(invocation -> {
            savedFile[0].markDeletionPending();
            return null;
        }).when(fileStorageService).storeFileAs(multipartFile, "image/jpeg", "storedFileName.jpg");

        assertThatThrownBy(() -> fileService.uploadFile(uploaderId, multipartFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_UPLOAD_ERROR);

        assertThat(savedFile[0].getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
    }

    @Test
    @DisplayName("업로드 권한이 없는 사용자는 스토리지 저장 전에 거절한다")
    void uploadFile_rejectsUnwritableUploaderBeforeStorage() {
        Long uploaderId = 1L;
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegHeader);

        when(userWritableResolver.resolve(uploaderId))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));

        assertThatThrownBy(() -> fileService.uploadFile(uploaderId, multipartFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(fileStorageService, never()).storeFileAs(any(), any(), any());
        verify(transactionTemplate, never()).execute(any());
        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("SVG 파일 업로드 차단")
    void uploadFile_rejectSvg() {
        Long uploaderId = 1L;
        MultipartFile multipartFile = new MockMultipartFile("file", "xss.svg", "image/svg+xml",
                "<svg><script>alert(1)</script></svg>".getBytes());

        assertThatThrownBy(() -> fileService.uploadFile(uploaderId, multipartFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);
        verify(userWritableResolver, never()).resolve(any());
        verify(fileStorageService, never()).storeFileAs(any(), any(), any());
    }

    @Test
    @DisplayName("업로더만 미연결 파일을 엔티티에 연결할 수 있다")
    void associateFileWithEntity_ownerAndTemporaryCheck() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);

        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        ReflectionTestUtils.setField(file, "fileId", 10L);

        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(fileRepository.associateIfUnassociated(10L, 1L, 100L, "POST_CONTENT")).thenReturn(1);

        assertThatThrownBy(() -> fileService.associateFileWithEntity(10L, 2L, 100L, "POST_CONTENT"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        fileService.associateFileWithEntity(10L, 1L, 100L, "POST_CONTENT");
        verify(fileRepository).associateIfUnassociated(10L, 1L, 100L, "POST_CONTENT");
        assertThat(file.getRelatedId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("이미 다른 엔티티에 연결된 파일은 재연결할 수 없다")
    void associateFileWithEntity_rejectsReassociation() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);

        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(11L)
                .relatedType("POST_CONTENT")
                .build();

        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.associateFileWithEntity(10L, 1L, 100L, "USER_PROFILE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);
    }

    @Test
    @DisplayName("같은 엔티티에 대한 재연결 요청은 무시한다")
    void associateFileWithEntity_sameAssociationIsNoOp() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);

        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(100L)
                .relatedType("POST_CONTENT")
                .build();

        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        fileService.associateFileWithEntity(10L, 1L, 100L, "POST_CONTENT");

        verify(fileRepository, never()).save(any(File.class));
    }

    @Test
    @DisplayName("여러 업로드 파일을 요청 순서대로 엔티티에 연결한다")
    void associateFilesWithEntity_associatesOwnedTemporaryFilesInOrder() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File firstFile = uploadedFile(10L, uploader);
        File secondFile = uploadedFile(11L, uploader);

        when(fileRepository.findByFileIdInAndStorageStatus(List.of(10L, 11L), FileStorageStatus.ACTIVE))
                .thenReturn(List.of(secondFile, firstFile));
        when(fileRepository.associateIfUnassociated(10L, 1L, 100L, "EMOTICON_IMAGE")).thenReturn(1);
        when(fileRepository.associateIfUnassociated(11L, 1L, 100L, "EMOTICON_IMAGE")).thenReturn(1);

        List<String> result = fileService.associateFilesWithEntity(
                List.of(10L, 11L),
                1L,
                100L,
                "EMOTICON_IMAGE");

        assertThat(result).containsExactly("/api/v1/files/10", "/api/v1/files/11");
        assertThat(firstFile.getRelatedId()).isEqualTo(100L);
        assertThat(secondFile.getRelatedType()).isEqualTo("EMOTICON_IMAGE");
        verify(fileRepository).associateIfUnassociated(10L, 1L, 100L, "EMOTICON_IMAGE");
        verify(fileRepository).associateIfUnassociated(11L, 1L, 100L, "EMOTICON_IMAGE");
    }

    @Test
    @DisplayName("여러 파일 연결 중 잘못된 소유자는 FORBIDDEN으로 거부한다")
    void associateFilesWithEntity_rejectsWrongOwner() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 2L);
        File file = uploadedFile(10L, uploader);

        when(fileRepository.findByFileIdInAndStorageStatus(List.of(10L), FileStorageStatus.ACTIVE))
                .thenReturn(List.of(file));

        assertThatThrownBy(() -> fileService.associateFilesWithEntity(
                List.of(10L),
                1L,
                100L,
                "EMOTICON_IMAGE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(fileRepository, never()).associateIfUnassociated(any(), any(), any(), any());
    }

    @Test
    @DisplayName("여러 파일 연결 중 null 파일 ID는 NOT_FOUND로 거부한다")
    void associateFilesWithEntity_rejectsNullFileId() {
        assertThatThrownBy(() -> fileService.associateFilesWithEntity(
                Arrays.asList((Long) null),
                1L,
                100L,
                "EMOTICON_IMAGE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(fileRepository, never()).associateIfUnassociated(any(), any(), any(), any());
    }

    @Test
    @DisplayName("프로필 이미지 교체 시 새 파일은 유지하고 기존 프로필 파일은 삭제 예정으로 전환한다")
    void replaceUserProfileImage_marksPreviousFilesPendingDelete() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);

        File newProfileFile = File.builder()
                .filePath("new-profile.jpg")
                .originalName("new-profile.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        ReflectionTestUtils.setField(newProfileFile, "fileId", 100L);

        File previousProfileFile = File.builder()
                .filePath("old-profile.jpg")
                .originalName("old-profile.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(1L)
                .relatedType(FileService.RELATED_TYPE_USER_PROFILE)
                .build();
        ReflectionTestUtils.setField(previousProfileFile, "fileId", 77L);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(uploader));
        when(fileRepository.findByFileIdAndStorageStatus(100L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(newProfileFile));
        when(fileRepository.associateIfUnassociated(100L, 1L, 1L, FileService.RELATED_TYPE_USER_PROFILE)).thenReturn(1);
        when(fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                1L,
                FileService.RELATED_TYPE_USER_PROFILE)).thenReturn(List.of(newProfileFile, previousProfileFile));

        String profileImageUrl = fileService.replaceUserProfileImage(100L, 1L, 1L);

        assertThat(profileImageUrl).isEqualTo("/api/v1/files/100");
        assertThat(previousProfileFile.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(newProfileFile.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("이미 사용자 잠금을 확보한 프로필 이미지 교체는 사용자 잠금을 반복하지 않는다")
    void replaceUserProfileImageForLockedUser_skipsUserLock() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);

        File newProfileFile = File.builder()
                .filePath("new-profile.jpg")
                .originalName("new-profile.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        ReflectionTestUtils.setField(newProfileFile, "fileId", 100L);

        File previousProfileFile = File.builder()
                .filePath("old-profile.jpg")
                .originalName("old-profile.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(1L)
                .relatedType(FileService.RELATED_TYPE_USER_PROFILE)
                .build();
        ReflectionTestUtils.setField(previousProfileFile, "fileId", 77L);

        when(fileRepository.findByFileIdAndStorageStatus(100L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(newProfileFile));
        when(fileRepository.associateIfUnassociated(100L, 1L, 1L, FileService.RELATED_TYPE_USER_PROFILE)).thenReturn(1);
        when(fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                1L,
                FileService.RELATED_TYPE_USER_PROFILE)).thenReturn(List.of(newProfileFile, previousProfileFile));

        String profileImageUrl = fileService.replaceUserProfileImageForLockedUser(100L, 1L, uploader);

        assertThat(profileImageUrl).isEqualTo("/api/v1/files/100");
        assertThat(previousProfileFile.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(newProfileFile.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("게시판 아이콘 교체 시 기존 아이콘 파일은 삭제 예정 상태로 전환한다")
    void replaceBoardIcon_marksPreviousFilesPendingDelete() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        Board board = Board.builder().boardName("board").boardUrl("board").creator(uploader).build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        File newBoardIcon = File.builder()
                .filePath("new-board-icon.jpg")
                .originalName("new-board-icon.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        ReflectionTestUtils.setField(newBoardIcon, "fileId", 200L);

        File previousBoardIcon = File.builder()
                .filePath("old-board-icon.jpg")
                .originalName("old-board-icon.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(10L)
                .relatedType(FileService.RELATED_TYPE_BOARD_ICON)
                .build();
        ReflectionTestUtils.setField(previousBoardIcon, "fileId", 150L);

        when(fileRepository.findByFileIdAndStorageStatus(200L, FileStorageStatus.ACTIVE))
                .thenReturn(Optional.of(newBoardIcon));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(fileRepository.associateIfUnassociated(200L, 1L, 10L, FileService.RELATED_TYPE_BOARD_ICON))
                .thenReturn(1);
        when(fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                10L,
                FileService.RELATED_TYPE_BOARD_ICON)).thenReturn(List.of(newBoardIcon, previousBoardIcon));

        String boardIconUrl = fileService.replaceBoardIcon(200L, 1L, 10L);

        assertThat(boardIconUrl).isEqualTo("/api/v1/files/200");
        assertThat(previousBoardIcon.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(newBoardIcon.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
    }

    @Test
    @DisplayName("게시글 첨부 파일을 한 번에 조회하고 연결한다")
    void attachFilesToPost_bulkAssociatesFiles() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File imageFile = File.builder()
                .filePath("image.jpg")
                .originalName("image.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        ReflectionTestUtils.setField(imageFile, "fileId", 10L);
        File draftFile = File.builder()
                .filePath("draft.jpg")
                .originalName("draft.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(99L)
                .relatedType(FileService.RELATED_TYPE_DRAFT_POST)
                .build();
        ReflectionTestUtils.setField(draftFile, "fileId", 11L);
        when(fileRepository.findByFileIdInAndStorageStatus(List.of(10L, 11L), FileStorageStatus.ACTIVE))
                .thenReturn(List.of(draftFile, imageFile));
        when(fileRepository.associateIfUnassociatedOrSourceDraft(
                eq(10L),
                eq(1L),
                eq(100L),
                eq(FileService.RELATED_TYPE_POST_CONTENT),
                eq(FileService.RELATED_TYPE_DRAFT_POST),
                eq(99L),
                any(LocalDateTime.class)))
                .thenReturn(1);
        when(fileRepository.associateIfUnassociatedOrSourceDraft(
                eq(11L),
                eq(1L),
                eq(100L),
                eq(FileService.RELATED_TYPE_POST_CONTENT),
                eq(FileService.RELATED_TYPE_DRAFT_POST),
                eq(99L),
                any(LocalDateTime.class)))
                .thenReturn(1);

        fileService.attachFilesToPost(List.of(10L, 11L, 10L), 1L, 100L, 99L);

        assertThat(imageFile.isAssociatedWith(100L, FileService.RELATED_TYPE_POST_CONTENT)).isTrue();
        assertThat(draftFile.isAssociatedWith(100L, FileService.RELATED_TYPE_POST_CONTENT)).isTrue();
        verify(fileRepository).findByFileIdInAndStorageStatus(List.of(10L, 11L), FileStorageStatus.ACTIVE);
        verify(fileRepository, never()).findByFileIdAndStorageStatus(any(), any());
    }

    @Test
    @DisplayName("게시글 첨부 파일 연결은 null 파일 ID를 거부한다")
    void attachFilesToPost_rejectsNullFileId() {
        assertThatThrownBy(() -> fileService.attachFilesToPost(Arrays.asList(10L, null), 1L, 100L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(fileRepository, never()).findByFileIdInAndStorageStatus(any(), any());
        verify(fileRepository, never()).associateIfUnassociatedOrSourceDraft(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("게시글 첨부 파일은 다른 초안에 묶인 파일을 거부한다")
    void attachFilesToPost_rejectsOtherDraftFile() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File draftFile = File.builder()
                .filePath("draft.jpg")
                .originalName("draft.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(99L)
                .relatedType(FileService.RELATED_TYPE_DRAFT_POST)
                .build();
        ReflectionTestUtils.setField(draftFile, "fileId", 11L);
        when(fileRepository.findByFileIdInAndStorageStatus(List.of(11L), FileStorageStatus.ACTIVE))
                .thenReturn(List.of(draftFile));

        assertThatThrownBy(() -> fileService.attachFilesToPost(List.of(11L), 1L, 100L, 77L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);

        verify(fileRepository, never()).associateIfUnassociatedOrSourceDraft(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("게시글 첨부 파일 경합 시 이미 다른 대상에 연결된 파일을 거부한다")
    void attachFilesToPost_rejectsConcurrentAssociationToOtherTarget() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File file = File.builder()
                .filePath("image.jpg")
                .originalName("image.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        ReflectionTestUtils.setField(file, "fileId", 10L);
        when(fileRepository.findByFileIdInAndStorageStatus(List.of(10L), FileStorageStatus.ACTIVE))
                .thenReturn(List.of(file));
        when(fileRepository.associateIfUnassociatedOrSourceDraft(
                eq(10L),
                eq(1L),
                eq(100L),
                eq(FileService.RELATED_TYPE_POST_CONTENT),
                eq(FileService.RELATED_TYPE_DRAFT_POST),
                isNull(),
                any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    file.updateRelatedInfo(200L, FileService.RELATED_TYPE_POST_CONTENT);
                    return 0;
                });

        assertThatThrownBy(() -> fileService.attachFilesToPost(List.of(10L), 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);

        verify(entityManager).refresh(file);
    }

    @Test
    @DisplayName("첨부 파일 bulk 검증은 누락 파일을 먼저 거부한다")
    void attachFilesToPost_missingFileThrowsNotFoundBeforeOwnerValidation() {
        User otherUploader = User.builder().build();
        ReflectionTestUtils.setField(otherUploader, "userId", 2L);
        File otherFile = File.builder()
                .filePath("other.jpg")
                .originalName("other.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(otherUploader)
                .build();
        ReflectionTestUtils.setField(otherFile, "fileId", 11L);
        when(fileRepository.findByFileIdInAndStorageStatus(List.of(10L, 11L), FileStorageStatus.ACTIVE))
                .thenReturn(List.of(otherFile));

        assertThatThrownBy(() -> fileService.attachFilesToPost(List.of(10L, 11L), 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("초안 파일 동기화는 다른 초안에 묶인 파일을 거부한다")
    void syncDraftFiles_rejectsOtherDraftFile() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File otherDraftFile = File.builder()
                .filePath("other-draft.jpg")
                .originalName("other-draft.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(99L)
                .relatedType(FileService.RELATED_TYPE_DRAFT_POST)
                .build();
        ReflectionTestUtils.setField(otherDraftFile, "fileId", 11L);
        when(fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(
                77L,
                FileService.RELATED_TYPE_DRAFT_POST,
                FileStorageStatus.ACTIVE)).thenReturn(List.of());
        when(fileRepository.findByFileIdInAndStorageStatus(List.of(11L), FileStorageStatus.ACTIVE))
                .thenReturn(List.of(otherDraftFile));

        assertThatThrownBy(() -> fileService.syncDraftFiles(List.of(11L), 1L, 77L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);

        verify(fileRepository, never()).associateIfUnassociatedOrSourceDraft(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("초안 파일 동기화는 null 파일 ID를 거부한다")
    void syncDraftFiles_rejectsNullFileId() {
        assertThatThrownBy(() -> fileService.syncDraftFiles(Arrays.asList(11L, null), 1L, 77L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(fileRepository, never()).findByRelatedIdAndRelatedTypeAndStorageStatus(any(), any(), any());
        verify(fileRepository, never()).findByFileIdInAndStorageStatus(any(), any());
        verify(fileRepository, never()).associateIfUnassociatedOrSourceDraft(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("게시글 파일 동기화는 요청 목록에 없는 기존 파일을 삭제 예정으로 전환한다")
    void syncPostFiles_marksOmittedFilesPendingDelete() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);

        File keptFile = File.builder()
                .filePath("kept.jpg")
                .originalName("kept.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(100L)
                .relatedType(FileService.RELATED_TYPE_POST_CONTENT)
                .build();
        ReflectionTestUtils.setField(keptFile, "fileId", 10L);

        File omittedFile = File.builder()
                .filePath("omitted.jpg")
                .originalName("omitted.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(100L)
                .relatedType(FileService.RELATED_TYPE_POST_CONTENT)
                .build();
        ReflectionTestUtils.setField(omittedFile, "fileId", 11L);

        when(fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                100L,
                FileService.RELATED_TYPE_POST_CONTENT)).thenReturn(List.of(keptFile, omittedFile));
        when(fileRepository.findByFileIdInAndStorageStatus(List.of(10L), FileStorageStatus.ACTIVE))
                .thenReturn(List.of(keptFile));

        fileService.syncPostFiles(List.of(10L), 1L, 100L);

        assertThat(keptFile.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(omittedFile.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
    }

    @Test
    @DisplayName("게시글 파일 동기화는 빈 목록을 모든 기존 파일 삭제로 처리한다")
    void syncPostFiles_emptyListMarksAllFilesPendingDelete() {
        File firstFile = File.builder()
                .filePath("first.jpg")
                .originalName("first.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(User.builder().build())
                .relatedId(100L)
                .relatedType(FileService.RELATED_TYPE_POST_CONTENT)
                .build();
        ReflectionTestUtils.setField(firstFile, "fileId", 10L);
        File secondFile = File.builder()
                .filePath("second.jpg")
                .originalName("second.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(User.builder().build())
                .relatedId(100L)
                .relatedType(FileService.RELATED_TYPE_POST_CONTENT)
                .build();
        ReflectionTestUtils.setField(secondFile, "fileId", 11L);

        when(fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                100L,
                FileService.RELATED_TYPE_POST_CONTENT)).thenReturn(List.of(firstFile, secondFile));

        fileService.syncPostFiles(List.of(), 1L, 100L);

        assertThat(firstFile.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(secondFile.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        verify(fileRepository, never()).findByFileIdInAndStorageStatus(any(), any());
    }

    @Test
    @DisplayName("게시글 파일 동기화는 null 파일 ID를 거부한다")
    void syncPostFiles_rejectsNullFileId() {
        assertThatThrownBy(() -> fileService.syncPostFiles(Arrays.asList(10L, null), 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(fileRepository, never()).findActiveByRelatedIdAndRelatedTypeForUpdate(any(), any());
        verify(fileRepository, never()).findByFileIdInAndStorageStatus(any(), any());
        verify(fileRepository, never()).associateIfUnassociatedOrSourceDraft(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("게시글 삭제 파일 정리는 관련 게시글 파일을 삭제 예정으로 전환한다")
    void markPostContentFilesDeletionPending_marksFilesPendingDelete() {
        File file = File.builder()
                .filePath("post.jpg")
                .originalName("post.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(User.builder().build())
                .relatedId(100L)
                .relatedType(FileService.RELATED_TYPE_POST_CONTENT)
                .build();
        ReflectionTestUtils.setField(file, "fileId", 10L);

        when(fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                100L,
                FileService.RELATED_TYPE_POST_CONTENT)).thenReturn(List.of(file));

        fileService.markPostContentFilesDeletionPending(100L);

        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
    }

    @Test
    @DisplayName("임시 파일 정리는 배치 worker를 커서로 반복 호출한다")
    void cleanUpTemporaryFiles_callsCleanupWorkerWithCursorUntilFinished() {
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 1);
        LocalDateTime thirdCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 2);
        when(fileTemporaryCleanupWorker.requestDeletionBatch(
                any(LocalDateTime.class),
                nullable(LocalDateTime.class),
                nullable(Long.class),
                eq(500),
                any(LocalDateTime.class)))
                .thenReturn(
                        FileTemporaryCleanupWorker.CleanupBatchResult.next(secondCreatedAt, 11L, 2, 2),
                        FileTemporaryCleanupWorker.CleanupBatchResult.next(thirdCreatedAt, 12L, 1, 1),
                        FileTemporaryCleanupWorker.CleanupBatchResult.completed());

        fileService.cleanUpTemporaryFiles();

        ArgumentCaptor<LocalDateTime> cursorCreatedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> cursorFileIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(fileTemporaryCleanupWorker, times(3)).requestDeletionBatch(
                any(LocalDateTime.class),
                cursorCreatedAtCaptor.capture(),
                cursorFileIdCaptor.capture(),
                eq(500),
                any(LocalDateTime.class));
        assertThat(cursorCreatedAtCaptor.getAllValues()).containsExactly(null, secondCreatedAt, thirdCreatedAt);
        assertThat(cursorFileIdCaptor.getAllValues()).containsExactly(null, 11L, 12L);
        verify(fileStorageService, never()).deleteFile(any());
        verify(fileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("삭제 대기 파일 ID 조회는 projection 결과를 그대로 반환한다")
    void getPendingDeletionFileIds_usesProjectionQuery() {
        when(fileRepository.findPendingDeletionFileIds(any(LocalDateTime.class), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(10L, 11L));

        List<Long> fileIds = fileService.getPendingDeletionFileIds(10);

        assertThat(fileIds).containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("삭제 요청이 없는 배치도 반환된 커서로 계속 진행한다")
    void cleanUpTemporaryFiles_continuesWhenBatchRequestsNothing() {
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 0);
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 1);
        when(fileTemporaryCleanupWorker.requestDeletionBatch(
                any(LocalDateTime.class),
                nullable(LocalDateTime.class),
                nullable(Long.class),
                eq(500),
                any(LocalDateTime.class)))
                .thenReturn(
                        FileTemporaryCleanupWorker.CleanupBatchResult.next(firstCreatedAt, 10L, 2, 0),
                        FileTemporaryCleanupWorker.CleanupBatchResult.next(secondCreatedAt, 11L, 1, 1),
                        FileTemporaryCleanupWorker.CleanupBatchResult.completed());

        fileService.cleanUpTemporaryFiles();

        ArgumentCaptor<LocalDateTime> cursorCreatedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> cursorFileIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(fileTemporaryCleanupWorker, times(3)).requestDeletionBatch(
                any(LocalDateTime.class),
                cursorCreatedAtCaptor.capture(),
                cursorFileIdCaptor.capture(),
                eq(500),
                any(LocalDateTime.class));
        assertThat(cursorCreatedAtCaptor.getAllValues()).containsExactly(null, firstCreatedAt, secondCreatedAt);
        assertThat(cursorFileIdCaptor.getAllValues()).containsExactly(null, 10L, 11L);
    }

    @Test
    @DisplayName("파일 삭제 요청은 즉시 DB 삭제 대신 pending 상태로 전환한다")
    void deleteFileWithStorage_marksFilePendingDelete() {
        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(User.builder().build())
                .build();

        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        boolean deleted = fileService.deleteFileWithStorage(10L);

        assertThat(deleted).isTrue();
        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        verify(fileStorageService, never()).deleteFile(any());
        verify(fileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("재시도 가능한 삭제 실패 파일 ID 조회는 projection 결과를 그대로 반환한다")
    void getRetryableFailedDeletionFileIds_usesProjectionQuery() {
        when(fileRepository.findRetryableFailedDeletionFileIds(5, PageRequest.of(0, 10)))
                .thenReturn(List.of(11L));

        List<Long> fileIds = fileService.getRetryableFailedDeletionFileIds(10);

        assertThat(fileIds).containsExactly(11L);
    }

    @Test
    @DisplayName("첫 이미지 파일 ID 목록 조회는 projection 쿼리를 사용한다")
    void getFirstImageFileIdsByRelatedIds_usesProjectionQuery() {
        List<Long> relatedIds = List.of(2L, 1L);
        when(fileRepository.findFirstImageFileIdsByRelatedIdsAndRelatedTypeAndMimeTypeStartingWithAndStorageStatus(
                relatedIds,
                FileService.RELATED_TYPE_POST_CONTENT,
                "image/",
                FileStorageStatus.ACTIVE))
                .thenReturn(List.of(
                        firstImageFileIdProjection(1L, 10L),
                        firstImageFileIdProjection(2L, 20L)));

        Map<Long, Long> firstImageFileIds = fileService.getFirstImageFileIdsByRelatedIds(
                relatedIds,
                FileService.RELATED_TYPE_POST_CONTENT);

        assertThat(firstImageFileIds).containsExactly(
                Map.entry(1L, 10L),
                Map.entry(2L, 20L));
        verify(fileRepository).findFirstImageFileIdsByRelatedIdsAndRelatedTypeAndMimeTypeStartingWithAndStorageStatus(
                relatedIds,
                FileService.RELATED_TYPE_POST_CONTENT,
                "image/",
                FileStorageStatus.ACTIVE);
    }

    private FirstImageFileIdProjection firstImageFileIdProjection(Long relatedId, Long fileId) {
        return new FirstImageFileIdProjection() {
            @Override
            public Long getRelatedId() {
                return relatedId;
            }

            @Override
            public Long getFileId() {
                return fileId;
            }
        };
    }

    private File uploadedFile(Long fileId, User uploader) {
        File file = File.builder()
                .filePath("stored-" + fileId + ".jpg")
                .originalName("test-" + fileId + ".jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        ReflectionTestUtils.setField(file, "fileId", fileId);
        return file;
    }

    private void stubSuccessfulUpload(
            MultipartFile multipartFile,
            String originalFilename,
            String mimeType,
            String storedFileName) {
        File[] savedFile = new File[1];
        when(fileStorageService.generateStoredFileName(originalFilename)).thenReturn(storedFileName);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<File> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            ReflectionTestUtils.setField(file, "fileId", 10L);
            savedFile[0] = file;
            return file;
        });
        when(fileRepository.findByIdForUpdate(10L)).thenAnswer(invocation -> Optional.of(savedFile[0]));
    }

    private static class HeaderLimitedInputStream extends InputStream {

        private final byte[] data;
        private final int maxReadableBytes;
        private int position;

        private HeaderLimitedInputStream(byte[] data, int maxReadableBytes) {
            this.data = data;
            this.maxReadableBytes = maxReadableBytes;
        }

        @Override
        public int read() throws IOException {
            if (position >= data.length) {
                return -1;
            }
            if (position >= maxReadableBytes) {
                throw new IOException("Read more than header bytes");
            }
            return data[position++] & 0xFF;
        }
    }

}
