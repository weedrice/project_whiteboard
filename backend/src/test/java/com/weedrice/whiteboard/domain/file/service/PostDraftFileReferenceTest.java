package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.post.dto.DraftResponse;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.port.PostUserReadPort;
import com.weedrice.whiteboard.domain.post.port.PostUserWritePort;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesRepository;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.post.service.PostDraftCleanupService;
import com.weedrice.whiteboard.domain.post.service.PostDraftService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostDraftFileReferenceTest {
    private final FileRepository fileRepository = mock(FileRepository.class);
    private final DraftPostRepository draftRepository = mock(DraftPostRepository.class);
    private final BoardRepository boardRepository = mock(BoardRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostUserWritePort userWritePort = mock(PostUserWritePort.class);
    private final List<File> files = new ArrayList<>();
    private final User owner = User.builder().build();
    private final Board board = Board.builder().boardUrl("free").build();
    private final Post originalPost = Post.builder().user(owner).board(board).build();
    private PostDraftService draftService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(owner, "userId", 1L);
        ReflectionTestUtils.setField(board, "boardId", 1L);
        ReflectionTestUtils.setField(originalPost, "postId", 50L);
        when(userWritePort.validateContentWriteForUpdate(1L)).thenReturn(owner);
        when(userWritePort.validate(1L)).thenReturn(owner);
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findById(50L)).thenReturn(Optional.of(originalPost));
        when(draftRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(invocation -> {
            DraftPost draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "draftId", 10L);
            when(draftRepository.findByDraftIdAndUserForUpdate(10L, 1L)).thenReturn(Optional.of(draft));
            return draft;
        });
        when(fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(anyLong(), anyString(), eq(FileStorageStatus.ACTIVE)))
                .thenAnswer(invocation -> associatedFiles(invocation.getArgument(0), invocation.getArgument(1)));
        when(fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(anyLong(), anyString()))
                .thenAnswer(invocation -> associatedFiles(invocation.getArgument(0), invocation.getArgument(1)));
        when(fileRepository.findByFileIdInAndStorageStatus(anyList(), eq(FileStorageStatus.ACTIVE)))
                .thenAnswer(invocation -> {
                    List<Long> requestedIds = invocation.getArgument(0);
                    return files.stream().filter(file -> requestedIds.contains(file.getFileId())
                            && file.getStorageStatus() == FileStorageStatus.ACTIVE).toList();
                });
        Clock clock = Clock.systemUTC();
        FileService fileService = new FileService(
                mock(FileUploadService.class),
                new FileAssociationService(fileRepository, mock(UserRepository.class), boardRepository, clock),
                fileRepository, mock(FileTemporaryCleanupWorker.class), clock);
        draftService = new PostDraftService(
                mock(PostUserReadPort.class), boardRepository, mock(BoardCategoryRepository.class), postRepository,
                mock(PostSeriesRepository.class), draftRepository, mock(ScheduledPostRepository.class), fileService,
                userWritePort, mock(BoardAccessPolicy.class), mock(PostAuthorCommandPolicy.class),
                mock(PostDraftCleanupService.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savingAndDeletingEditDraftPreservesOriginalAttachments(boolean existingDraft) {
        File original = file(11L, owner, 50L, FileService.RELATED_TYPE_POST_CONTENT);
        File draftAttachment = file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);
        if (existingDraft) {
            DraftPost draft = DraftPost.builder().user(owner).board(board).originalPost(originalPost).build();
            ReflectionTestUtils.setField(draft, "draftId", 10L);
            ReflectionTestUtils.setField(draft, "version", 2L);
            when(draftRepository.findByDraftIdAndUserForUpdate(10L, 1L)).thenReturn(Optional.of(draft));
        }
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(existingDraft ? 10L : null).version(existingDraft ? 2L : null)
                .boardUrl("free").originalPostId(50L).title("Changed title")
                .contents("<p>Original</p><img src=\"/api/v1/files/11\">"
                        + "<a href=\"/files/11?download=true\">Original attachment</a>"
                        + "<img src=\"/api/v1/files/12\">")
                .fileIds(List.of(12L)).build();

        DraftResponse response = draftService.saveDraftPost(1L, request);

        assertThat(response.getContents()).contains("/api/v1/files/11", "/files/11?download=true", "/api/v1/files/12");
        assertThat(response.getFileIds()).containsExactly(12L);
        assertThat(response.isStaleReferencesReset()).isFalse();
        assertThat(original.isAssociatedWith(50L, FileService.RELATED_TYPE_POST_CONTENT)).isTrue();
        assertThat(original.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(draftAttachment.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        verify(fileRepository).findByRelatedIdAndRelatedTypeAndStorageStatus(
                50L, FileService.RELATED_TYPE_POST_CONTENT, FileStorageStatus.ACTIVE);

        draftService.deleteDraftPost(1L, 10L, null);

        assertThat(draftAttachment.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(original.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(original.isAssociatedWith(50L, FileService.RELATED_TYPE_POST_CONTENT)).isTrue();
    }

    @Test
    void originalFileIdsInRequestAreNotMovedIntoDraftOwnership() {
        File original = file(11L, owner, 50L, FileService.RELATED_TYPE_POST_CONTENT);
        file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);

        DraftResponse response = draftService.saveDraftPost(1L, editRequest(
                "<img src=\"/api/v1/files/11\"><img src=\"/api/v1/files/12\">", List.of(11L, 12L)));

        assertThat(response.getContents()).contains("/api/v1/files/11", "/api/v1/files/12");
        assertThat(response.getFileIds()).containsExactly(12L);
        assertThat(response.isStaleReferencesReset()).isFalse();
        assertThat(original.isAssociatedWith(50L, FileService.RELATED_TYPE_POST_CONTENT)).isTrue();
        assertThat(original.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
    }

    @Test
    void editDraftRemovesUnavailableAndForeignReferencesWhileKeepingValidPostAndDraftFiles() {
        file(11L, owner, 50L, FileService.RELATED_TYPE_POST_CONTENT);
        file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);
        File anotherPost = file(13L, owner, 51L, FileService.RELATED_TYPE_POST_CONTENT);
        User anotherUser = User.builder().build();
        ReflectionTestUtils.setField(anotherUser, "userId", 2L);
        File foreign = file(14L, anotherUser, 50L, FileService.RELATED_TYPE_POST_CONTENT);
        File deleted = file(15L, owner, 50L, FileService.RELATED_TYPE_POST_CONTENT);
        deleted.markDeletionPending(java.time.LocalDateTime.now());
        File anotherDraft = file(16L, owner, 99L, FileService.RELATED_TYPE_DRAFT_POST);
        String contents = java.util.stream.LongStream.rangeClosed(11, 17)
                .mapToObj(id -> "<img src=\"/api/v1/files/" + id + "\">")
                .collect(java.util.stream.Collectors.joining());

        DraftResponse response = draftService.saveDraftPost(1L,
                editRequest(contents, List.of(12L, 13L, 14L, 15L, 16L, 17L)));

        assertThat(response.getContents()).contains("/api/v1/files/11", "/api/v1/files/12")
                .doesNotContain("/api/v1/files/13", "/api/v1/files/14", "/api/v1/files/15", "/api/v1/files/16", "/api/v1/files/17");
        assertThat(response.getFileIds()).containsExactly(12L);
        assertThat(response.isStaleReferencesReset()).isTrue();
        assertThat(anotherPost.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(foreign.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(deleted.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(anotherDraft.isAssociatedWith(99L, FileService.RELATED_TYPE_DRAFT_POST)).isTrue();
    }

    @Test
    void newPostDraftDoesNotAdoptExistingPostAttachments() {
        File original = file(11L, owner, 50L, FileService.RELATED_TYPE_POST_CONTENT);
        PostDraftRequest request = PostDraftRequest.builder().boardUrl("free").title("New post")
                .contents("<img src=\"/api/v1/files/11\">").fileIds(List.of(11L)).build();

        DraftResponse response = draftService.saveDraftPost(1L, request);

        assertThat(response.getContents()).doesNotContain("/api/v1/files/11");
        assertThat(response.getFileIds()).isEmpty();
        assertThat(response.isStaleReferencesReset()).isTrue();
        assertThat(original.isAssociatedWith(50L, FileService.RELATED_TYPE_POST_CONTENT)).isTrue();
        assertThat(original.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        verify(fileRepository, never()).findByRelatedIdAndRelatedTypeAndStorageStatus(
                anyLong(), anyString(), any());
    }

    private PostDraftRequest editRequest(String contents, List<Long> fileIds) {
        return PostDraftRequest.builder().boardUrl("free").originalPostId(50L).title("Changed title")
                .contents(contents).fileIds(fileIds).build();
    }

    private File file(Long id, User uploader, Long relatedId, String relatedType) {
        File file = File.builder().uploader(uploader).relatedId(relatedId).relatedType(relatedType).build();
        ReflectionTestUtils.setField(file, "fileId", id);
        files.add(file);
        return file;
    }

    private List<File> associatedFiles(Long relatedId, String relatedType) {
        return files.stream().filter(file -> file.isAssociatedWith(relatedId, relatedType)
                && file.getStorageStatus() == FileStorageStatus.ACTIVE).toList();
    }
}
