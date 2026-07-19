package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAccessServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BoardAccessPolicy boardAccessPolicy;
    @Mock
    private PostAccessPolicy postAccessPolicy;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private EmoticonMasterRepository emoticonMasterRepository;

    private FileAccessService fileAccessService;

    @BeforeEach
    void setUp() {
        fileAccessService = new FileAccessService(
                fileRepository,
                boardRepository,
                postRepository,
                userRepository,
                boardAccessPolicy,
                postAccessPolicy,
                userBlockService,
                emoticonMasterRepository);
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
    @DisplayName("활성 사용자의 현재 프로필 이미지는 공개한다")
    void getFileForDownload_currentActiveUserProfile_allowsAnonymous() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 100L);
        owner.updateProfileImage("/api/v1/files/10");
        File file = file(FileRelatedType.USER_PROFILE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE))
                .thenReturn(Optional.of(file));
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(100L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(owner));

        File result = fileAccessService.getFileForDownload(10L, null);

        assertThat(result).isSameAs(file);
        verify(userBlockService, never()).isEitherDirectionBlocked(anyLong(), anyLong());
    }

    @Test
    @DisplayName("차단 관계인 사용자의 현재 프로필 이미지는 직접 다운로드할 수 없다")
    void getFileForDownload_blockedUserProfile_notFound() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 100L);
        owner.updateProfileImage("/api/v1/files/10");
        File file = file(FileRelatedType.USER_PROFILE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE))
                .thenReturn(Optional.of(file));
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(100L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(owner));
        when(userBlockService.isEitherDirectionBlocked(1L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(userBlockService).isEitherDirectionBlocked(1L, 100L);
    }

    @Test
    @DisplayName("차단 관계가 아닌 로그인 사용자는 현재 프로필 이미지를 다운로드할 수 있다")
    void getFileForDownload_unblockedUserProfile_allowsAuthenticatedViewer() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 100L);
        owner.updateProfileImage("/api/v1/files/10");
        File file = file(FileRelatedType.USER_PROFILE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE))
                .thenReturn(Optional.of(file));
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(100L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(owner));
        when(userBlockService.isEitherDirectionBlocked(1L, 100L)).thenReturn(false);

        File result = fileAccessService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
        verify(userBlockService).isEitherDirectionBlocked(1L, 100L);
    }

    @Test
    @DisplayName("본인은 차단 관계 조회 없이 현재 프로필 이미지를 다운로드할 수 있다")
    void getFileForDownload_ownUserProfile_skipsBlockCheck() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 100L);
        owner.updateProfileImage("/api/v1/files/10");
        File file = file(FileRelatedType.USER_PROFILE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE))
                .thenReturn(Optional.of(file));
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(100L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(owner));

        File result = fileAccessService.getFileForDownload(10L, 100L);

        assertThat(result).isSameAs(file);
        verify(userBlockService, never()).isEitherDirectionBlocked(anyLong(), anyLong());
    }

    @Test
    @DisplayName("탈퇴했거나 현재 프로필이 아닌 이미지는 숨긴다")
    void getFileForDownload_staleUserProfile_notFound() {
        File file = file(FileRelatedType.USER_PROFILE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE))
                .thenReturn(Optional.of(file));
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(100L, User.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("board icon files validate board read policy")
    void getFileForDownload_boardIcon_validatesBoardReadPolicy() {
        File file = file(FileRelatedType.BOARD_ICON, 100L);
        Board board = board();
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(boardRepository.findByBoardId(100L)).thenReturn(Optional.of(board));

        File result = fileAccessService.getFileForDownload(10L, null);

        assertThat(result).isSameAs(file);
        verify(boardRepository).findByBoardId(100L);
        verify(boardAccessPolicy).validateReadable(board, null);
        verifyNoInteractions(postRepository, userRepository, postAccessPolicy, userBlockService);
    }

    @Test
    @DisplayName("board icon files reject unreadable boards")
    void getFileForDownload_boardIcon_unreadableBoardNotFound() {
        File file = file(FileRelatedType.BOARD_ICON, 100L);
        Board board = board();
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(boardRepository.findByBoardId(100L)).thenReturn(Optional.of(board));
        doThrow(new BusinessException(ErrorCode.BOARD_NOT_FOUND))
                .when(boardAccessPolicy).validateReadable(board, null);

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
    }

    @Test
    @DisplayName("board icon files validate authenticated viewers with board read policy")
    void getFileForDownload_boardIcon_authenticatedViewerUsesBoardPolicy() {
        User viewer = User.builder().build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);
        File file = file(FileRelatedType.BOARD_ICON, 100L);
        Board board = board();
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(boardRepository.findByBoardId(100L)).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));

        File result = fileAccessService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
        verify(boardAccessPolicy).validateReadable(board, viewer);
    }

    @Test
    @DisplayName("missing board icon board is hidden")
    void getFileForDownload_boardIcon_missingBoardNotFound() {
        File file = file(FileRelatedType.BOARD_ICON, 100L);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(boardRepository.findByBoardId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(boardAccessPolicy, never()).validateReadable(any(), any());
    }

    @Test
    @DisplayName("active emoticon files are public")
    void getFileForDownload_activeEmoticonFile_allowsAnonymous() {
        File file = file(FileRelatedType.EMOTICON_IMAGE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, User.builder().build(), true);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));

        File result = fileAccessService.getFileForDownload(10L, null);

        assertThat(result).isSameAs(file);
        verifyNoInteractions(postRepository, userRepository, postAccessPolicy, userBlockService);
    }

    @Test
    void getFileAccessForDownload_activeEmoticonFile_isPubliclyCacheable() {
        File file = file(FileRelatedType.EMOTICON_IMAGE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, User.builder().build(), true);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));

        FileAccessResult result = fileAccessService.getFileAccessForDownload(10L, null);

        assertThat(result.file()).isSameAs(file);
        assertThat(result.cacheScope()).isEqualTo(FileAccessResult.CacheScope.PUBLIC_EMOTICON);
    }

    @Test
    @DisplayName("inactive emoticon files are available to the owner")
    void getFileForDownload_inactiveEmoticonFile_allowsOwner() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 1L);
        File file = file(FileRelatedType.EMOTICON_THUMBNAIL, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, owner, false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));

        File result = fileAccessService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
        verify(emoticonMasterRepository, never()).canUseAllEmoticons(anyLong(), anyList(), anyLong());
    }

    @Test
    void getFileAccessForDownload_inactiveEmoticonFile_isRestricted() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 1L);
        File file = file(FileRelatedType.EMOTICON_THUMBNAIL, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, owner, false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));

        FileAccessResult result = fileAccessService.getFileAccessForDownload(10L, 1L);

        assertThat(result.cacheScope()).isEqualTo(FileAccessResult.CacheScope.RESTRICTED_EMOTICON);
    }

    @Test
    @DisplayName("inactive emoticon files are available to entitled users")
    void getFileForDownload_inactiveEmoticonFile_allowsEntitledUser() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 2L);
        File file = file(FileRelatedType.EMOTICON_IMAGE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, owner, false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));
        when(emoticonMasterRepository.canUseAllEmoticons(1L, List.of(100L), 1L)).thenReturn(true);

        File result = fileAccessService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
        verify(emoticonMasterRepository).canUseAllEmoticons(1L, List.of(100L), 1L);
    }

    @Test
    @DisplayName("inactive emoticon file entitlement checks distinct emoticon ids once")
    void getFileForDownload_inactiveEmoticonFile_checksDistinctEmoticonIdsOnce() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 2L);
        File file = file(FileRelatedType.EMOTICON_IMAGE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster firstMaster = emoticonMaster(100L, owner, false);
        EmoticonMaster duplicateMaster = emoticonMaster(100L, owner, false);
        EmoticonMaster secondMaster = emoticonMaster(200L, owner, false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(firstMaster, duplicateMaster, secondMaster));
        when(emoticonMasterRepository.canUseAllEmoticons(1L, List.of(100L, 200L), 2L)).thenReturn(true);

        File result = fileAccessService.getFileForDownload(10L, 1L);

        assertThat(result).isSameAs(file);
        verify(emoticonMasterRepository).canUseAllEmoticons(1L, List.of(100L, 200L), 2L);
    }

    @Test
    @DisplayName("inactive emoticon file rejects users without all entitlements")
    void getFileForDownload_inactiveEmoticonFile_partialEntitlementForbidden() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 2L);
        File file = file(FileRelatedType.EMOTICON_IMAGE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster firstMaster = emoticonMaster(100L, owner, false);
        EmoticonMaster secondMaster = emoticonMaster(200L, owner, false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(firstMaster, secondMaster));
        when(emoticonMasterRepository.canUseAllEmoticons(1L, List.of(100L, 200L), 2L)).thenReturn(false);

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("active emoticon match does not bypass inactive match")
    void getFileForDownload_activeAndInactiveEmoticonFile_anonymousForbidden() {
        File file = file(FileRelatedType.EMOTICON_IMAGE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster activeMaster = emoticonMaster(100L, User.builder().build(), true);
        EmoticonMaster inactiveMaster = emoticonMaster(200L, User.builder().build(), false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(activeMaster, inactiveMaster));

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("inactive emoticon files reject anonymous users")
    void getFileForDownload_inactiveEmoticonFile_anonymousForbidden() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 2L);
        File file = file(FileRelatedType.EMOTICON_IMAGE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, owner, false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("inactive emoticon files reject users without entitlement")
    void getFileForDownload_inactiveEmoticonFile_unauthorizedForbidden() {
        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "userId", 2L);
        File file = file(FileRelatedType.EMOTICON_IMAGE, 100L);
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, owner, false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(100L, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));
        when(emoticonMasterRepository.canUseAllEmoticons(1L, List.of(100L), 1L)).thenReturn(false);

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("draft files are available to uploaders only")
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
    @DisplayName("legacy active emoticon file URL references are public")
    void getFileForDownload_legacyActiveEmoticonFile_allowsAnonymous() {
        File file = file(null, null, User.builder().build());
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, User.builder().build(), true);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(null, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));

        File result = fileAccessService.getFileForDownload(10L, null);

        assertThat(result).isSameAs(file);
        verifyNoInteractions(postRepository, userRepository, postAccessPolicy, userBlockService);
    }

    @Test
    void getFileAccessForDownload_legacyActiveEmoticonFile_isPubliclyCacheable() {
        File file = file(null, null, User.builder().build());
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, User.builder().build(), true);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(null, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));

        FileAccessResult result = fileAccessService.getFileAccessForDownload(10L, null);

        assertThat(result.cacheScope()).isEqualTo(FileAccessResult.CacheScope.PUBLIC_EMOTICON);
    }

    @Test
    @DisplayName("legacy inactive emoticon file URL references reject anonymous users")
    void getFileForDownload_legacyInactiveEmoticonFile_anonymousForbidden() {
        File file = file(null, null, User.builder().build());
        ReflectionTestUtils.setField(file, "fileId", 10L);
        EmoticonMaster master = emoticonMaster(100L, User.builder().build(), false);
        when(fileRepository.findByFileIdAndStorageStatus(10L, FileStorageStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(emoticonMasterRepository.findFileAccessTargets(null, List.of("/api/v1/files/10", "/files/10")))
                .thenReturn(List.of(master));

        assertThatThrownBy(() -> fileAccessService.getFileForDownload(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
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

    private Board board() {
        return Board.builder()
                .boardName("test-board")
                .boardUrl("test-board")
                .creator(User.builder().build())
                .build();
    }

    private EmoticonMaster emoticonMaster(Long emoticonId, User creator, boolean active) {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("emoticon")
                .thumbnailUrl("/api/v1/files/10")
                .creator(creator)
                .build();
        ReflectionTestUtils.setField(master, "emoticonId", emoticonId);
        if (!active) {
            master.deactivate();
        }
        return master;
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
