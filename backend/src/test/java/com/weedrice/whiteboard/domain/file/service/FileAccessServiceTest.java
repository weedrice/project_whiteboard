package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAccessServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostAccessPolicy postAccessPolicy;
    @Mock
    private UserBlockService userBlockService;

    private FileAccessService fileAccessService;

    @BeforeEach
    void setUp() {
        fileAccessService = new FileAccessService(
                fileRepository,
                postRepository,
                userRepository,
                postAccessPolicy,
                userBlockService);
    }

    @Test
    @DisplayName("게시글 첨부 다운로드는 연관 조회로 게시글 읽기 권한을 검증한다")
    void getFileForDownload_postContent_validatesReadableWithRelations() {
        User viewer = User.builder().build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);
        User author = User.builder().build();
        ReflectionTestUtils.setField(author, "userId", 2L);
        Post post = Post.builder().user(author).build();
        File file = File.builder()
                .relatedId(100L)
                .relatedType(FileRelatedType.POST_CONTENT)
                .build();

        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(true);

        File result = fileAccessService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
        verify(postRepository).findByIdWithRelations(100L);
        verify(postAccessPolicy).validateReadable(post, viewer, true);
        verify(userBlockService).isEitherDirectionBlocked(1L, 2L);
    }

    @Test
    @DisplayName("차단된 게시글 첨부 다운로드는 게시글 숨김 예외를 유지한다")
    void getFileForDownload_authorBlockedViewer_notFound() {
        User viewer = User.builder().build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);
        User author = User.builder().build();
        ReflectionTestUtils.setField(author, "userId", 2L);
        Post post = Post.builder().user(author).build();
        File file = file(FileRelatedType.POST_CONTENT, 100L);

        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(true);
        doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(postAccessPolicy).validateReadable(post, viewer, true);

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("활성 파일이 없으면 숨김 처리한다")
    void getFileForDownload_missingActiveFile_notFound() {
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("공개 파일 타입은 추가 접근 정책 검증을 생략한다")
    void getFileForDownload_publicTypes_skipAccessPolicy() {
        List<String> relatedTypes = List.of(
                FileRelatedType.USER_PROFILE,
                FileRelatedType.BOARD_ICON,
                FileRelatedType.EMOTICON_IMAGE,
                FileRelatedType.EMOTICON_THUMBNAIL);

        for (int i = 0; i < relatedTypes.size(); i++) {
            Long fileId = 10L + i;
            File file = file(relatedTypes.get(i), 100L + i);
            when(fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE))
                    .thenReturn(Optional.of(file));

            File result = fileAccessService.getFileForDownload(fileId, null);

            assertThat(result).isSameAs(file);
        }
        verifyNoInteractions(postRepository, userRepository, postAccessPolicy, userBlockService);
    }

    @Test
    @DisplayName("초안 파일은 업로더만 다운로드할 수 있다")
    void getFileForDownload_draftFile_allowsUploaderOnly() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File file = file(FileRelatedType.DRAFT_POST, 100L, uploader);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        File result = fileAccessService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
    }

    @Test
    @DisplayName("익명 사용자는 초안 파일을 다운로드할 수 없다")
    void getFileForDownload_draftFile_anonymousForbidden() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File file = file(FileRelatedType.DRAFT_POST, 100L, uploader);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("미연결 임시 파일은 업로더만 다운로드할 수 있다")
    void getFileForDownload_temporaryFile_allowsUploaderOnly() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File file = file(null, null, uploader);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        File result = fileAccessService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
    }

    @Test
    @DisplayName("부분 연결 파일은 다운로드를 거부한다")
    void getFileForDownload_partiallyAssociatedFile_forbidden() {
        User uploader = User.builder().build();
        ReflectionTestUtils.setField(uploader, "userId", 1L);
        File file = file(null, 100L, uploader);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("알 수 없는 연결 타입은 다운로드를 거부한다")
    void getFileForDownload_unknownRelatedType_forbidden() {
        File file = file("UNKNOWN", 100L);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    private File file(String relatedType, Long relatedId) {
        return file(relatedType, relatedId, User.builder().build());
    }

    private File file(String relatedType, Long relatedId, User uploader) {
        return File.builder()
                .filePath("storedFileName.jpg")
                .originalName("test.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .build();
    }
}
