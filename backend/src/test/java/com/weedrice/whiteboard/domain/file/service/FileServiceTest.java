package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private PostRepository postRepository;
    @Mock
    private PostAccessPolicy postAccessPolicy;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private FileService fileService;

    @Test
    @DisplayName("파일 업로드 성공")
    void uploadFile_success() {
        Long uploaderId = 1L;
        User uploader = User.builder().build();
        byte[] jpegHeader = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        MultipartFile multipartFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegHeader);
        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(10L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();

        when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
        when(fileStorageService.storeFile(multipartFile)).thenReturn("storedFileName.jpg");
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<File> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(fileRepository.save(any(File.class))).thenReturn(file);

        FileUploadResponse uploadedFile = fileService.uploadFile(uploaderId, multipartFile);

        assertThat(uploadedFile.getOriginalName()).isEqualTo("test.jpg");
        assertThat(uploadedFile.getStoredName()).isEqualTo("storedFileName.jpg");
        assertThat(uploadedFile.getFileUrl()).isEqualTo("/api/v1/files/" + file.getFileId());
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

        when(fileRepository.findByFileIdAndStorageStatus(100L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(newProfileFile));
        when(fileRepository.associateIfUnassociated(100L, 1L, 1L, FileService.RELATED_TYPE_USER_PROFILE)).thenReturn(1);
        when(fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(
                1L,
                FileService.RELATED_TYPE_USER_PROFILE,
                FileStorageStatus.ACTIVE)).thenReturn(List.of(newProfileFile, previousProfileFile));

        String profileImageUrl = fileService.replaceUserProfileImage(100L, 1L, 1L);

        assertThat(profileImageUrl).isEqualTo("/api/v1/files/100");
        assertThat(previousProfileFile.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(newProfileFile.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
    }

    @Test
    @DisplayName("임시 파일 정리는 바로 삭제하지 않고 pending 상태로 전환한다")
    void cleanUpTemporaryFiles_marksFilesPendingDeleteInBatches() {
        when(fileRepository.findTemporaryFileIdsForCleanup(any(LocalDateTime.class), eq(PageRequest.of(0, 500))))
                .thenReturn(List.of(10L, 11L), List.of(12L), List.of());

        fileService.cleanUpTemporaryFiles();

        verify(fileRepository).requestDeletionForTemporaryFiles(eq(List.of(10L, 11L)), any(LocalDateTime.class));
        verify(fileRepository).requestDeletionForTemporaryFiles(eq(List.of(12L)), any(LocalDateTime.class));
        verify(fileRepository, times(3)).findTemporaryFileIdsForCleanup(any(LocalDateTime.class),
                eq(PageRequest.of(0, 500)));
        verify(fileStorageService, never()).deleteFile(any());
        verify(fileRepository, never()).delete(any());
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
    @DisplayName("DELETE_FAILED ?뚯씪???ъ떆?쒕룄 ??닔瑜?珥덇낵?섎㈃ ?묒뾽 ??곸뿉???쒖쇅?쒕떎")
    void getRetryableFailedDeletionFileIds_excludesFilesOverRetryLimit() {
        File pendingDeleteFile = File.builder()
                .filePath("pending.jpg")
                .originalName("pending.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(User.builder().build())
                .storageStatus(FileStorageStatus.PENDING_DELETE)
                .build();
        ReflectionTestUtils.setField(pendingDeleteFile, "fileId", 10L);

        File retryableFailedFile = File.builder()
                .filePath("failed.jpg")
                .originalName("failed.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(User.builder().build())
                .storageStatus(FileStorageStatus.DELETE_FAILED)
                .deleteRetryCount(4)
                .build();
        ReflectionTestUtils.setField(retryableFailedFile, "fileId", 11L);

        File exhaustedFailedFile = File.builder()
                .filePath("exhausted.jpg")
                .originalName("exhausted.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(User.builder().build())
                .storageStatus(FileStorageStatus.DELETE_FAILED)
                .deleteRetryCount(5)
                .build();
        ReflectionTestUtils.setField(exhaustedFailedFile, "fileId", 12L);

        when(fileRepository.findRetryableFailedDeletionCandidates(5, PageRequest.of(0, 10)))
                .thenReturn(new ArrayList<>(List.of(retryableFailedFile)));

        List<Long> fileIds = fileService.getRetryableFailedDeletionFileIds(10);

        assertThat(fileIds).containsExactly(11L);
    }

    @Test
    @DisplayName("게시글 첨부 다운로드는 게시글 읽기 권한을 검증한다")
    void getFileForDownload_validatesPostAccessForPostContent() {
        User uploader = User.builder().build();
        User viewer = User.builder().build();
        Post post = Post.builder().build();
        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(100L)
                .relatedType(FileService.RELATED_TYPE_POST_CONTENT)
                .build();

        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));

        File result = fileService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
        verify(postAccessPolicy).validateReadable(post, viewer);
    }

    @Test
    @DisplayName("삭제 예정 파일은 다운로드할 수 없다")
    void getFileForDownload_rejectsPendingDeletionFile() {
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 첨부가 아니면 추가 권한 검증 없이 다운로드한다")
    void getFileForDownload_skipsAccessPolicyForPublicTypes() {
        User uploader = User.builder().build();
        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("profile.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(100L)
                .relatedType("USER_PROFILE")
                .build();

        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        File result = fileService.getFileForDownload(10L, null);

        assertThat(result).isSameAs(file);
        verify(postRepository, never()).findById(any());
        verify(postAccessPolicy, never()).validateReadable(any(), any());
    }
}
