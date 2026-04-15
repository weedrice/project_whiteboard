package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @DisplayName("파일 소유자만 엔티티 연결 가능")
    void associateFileWithEntity_ownerCheck() {
        User uploader = User.builder().build();
        org.springframework.test.util.ReflectionTestUtils.setField(uploader, "userId", 1L);

        File file = File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(file, "fileId", 10L);

        when(fileRepository.findById(10L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.associateFileWithEntity(10L, 2L, 100L, "POST_CONTENT"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        fileService.associateFileWithEntity(10L, 1L, 100L, "POST_CONTENT");
        verify(fileRepository).save(eq(file));
    }

    @Test
    @DisplayName("게시글 첨부 다운로드는 게시글 읽기 정책을 검증한다")
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

        when(fileRepository.findById(10L)).thenReturn(Optional.of(file));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));

        File result = fileService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
        verify(postAccessPolicy).validateReadable(post, viewer);
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

        when(fileRepository.findById(10L)).thenReturn(Optional.of(file));

        File result = fileService.getFileForDownload(10L, null);

        assertThat(result).isSameAs(file);
        verify(postRepository, never()).findById(any());
        verify(postAccessPolicy, never()).validateReadable(any(), any());
    }
}
