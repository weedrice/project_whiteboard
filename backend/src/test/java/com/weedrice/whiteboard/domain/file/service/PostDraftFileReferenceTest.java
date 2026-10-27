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
import com.weedrice.whiteboard.domain.post.service.PostDraftCleanupBatchService;
import com.weedrice.whiteboard.domain.post.service.PostDraftService;
import com.weedrice.whiteboard.domain.post.support.PostContentCodec;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostDraftFileReferenceTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "https://noviis.kr", "https://NOVIIS.kr:443", "//noviis.kr"})
    void staleViewportFilesAreRemovedAndTheReconciledDraftCanBeSavedAgain(String origin) {
        File attachment = file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);
        File original = file(11L, owner, 50L, FileService.RELATED_TYPE_POST_CONTENT);
        String source = "<template data-noviis-layer=\"viewport\"><img src=\"" + origin + "/api/v1/files/12\">"
                + "<img src=\"" + origin + "/api/v1/files/99\"></template>"
                + "<img src=\"" + origin + "/api/v1/files/11\">"
                + "<a href=\"" + origin + "/api/v1/files/99\">Attachment label</a>";

        DraftResponse saved = draftService.saveDraftPost(1L, editRequest(preservedHtml(source), List.of(12L, 99L)));

        assertThat(saved.getFileIds()).containsExactly(12L);
        assertThat(saved.isStaleReferencesReset()).isTrue();
        String reconciled = PostContentCodec.expandPreservedHtml(saved.getContents());
        assertThat(reconciled).contains(origin + "/api/v1/files/12", origin + "/api/v1/files/11", "Attachment label")
                .doesNotContain("/api/v1/files/99", "<a ");
        PostDraftRequest retry = PostDraftRequest.builder()
                .draftId(saved.getDraftId()).version(saved.getVersion()).boardUrl("free").originalPostId(50L)
                .title("Changed title").contents(saved.getContents()).fileIds(saved.getFileIds()).build();

        DraftResponse resaved = draftService.saveDraftPost(1L, retry);

        assertThat(resaved.isStaleReferencesReset()).isFalse();
        assertThat(resaved.getContents()).isEqualTo(saved.getContents());
        assertThat(resaved.getFileIds()).containsExactly(12L);
        assertThat(attachment.isAssociatedWith(10L, FileService.RELATED_TYPE_DRAFT_POST)).isTrue();
        assertThat(original.isAssociatedWith(50L, FileService.RELATED_TYPE_POST_CONTENT)).isTrue();
        assertThat(original.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
    }

    @Test
    void staleAbsoluteViewportReferenceIsDetectedWithoutARequestedFileId() {
        String source = "<template data-noviis-layer=\"viewport\"><img src=\"https://noviis.kr/api/v1/files/99\"></template>";
        DraftResponse response = draftService.saveDraftPost(1L, editRequest(preservedHtml(source), List.of()));
        assertThat(response.isStaleReferencesReset()).isTrue();
        assertThat(PostContentCodec.expandPreservedHtml(response.getContents())).doesNotContain("/api/v1/files/99");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://noviis.kr", "https://noviis.kr:8443", "https://user@noviis.kr"})
    void draftReconciliationDoesNotTreatOtherOriginsOrUserInfoAsLocalFiles(String origin) {
        String source = "<template data-noviis-layer=\"viewport\"><img src=\"" + origin + "/api/v1/files/99\"></template>";
        DraftResponse response = draftService.saveDraftPost(1L, editRequest(preservedHtml(source), List.of(99L)));
        assertThat(response.getFileIds()).isEmpty();
        assertThat(PostContentCodec.expandPreservedHtml(response.getContents())).isEqualTo(source);
    }

    @Test
    void draftReferenceResolutionUsesTheConfiguredFrontendOrigin() {
        ReflectionTestUtils.setField(draftService, "frontendOrigin", java.net.URI.create("https://staging.noviis.kr:8443"));
        String source = "<template data-noviis-layer=\"viewport\">"
                + "<img src=\"https://staging.noviis.kr:8443/api/v1/files/99\">"
                + "<img src=\"https://noviis.kr/api/v1/files/99\"></template>";
        DraftResponse response = draftService.saveDraftPost(1L, editRequest(preservedHtml(source), List.of()));
        assertThat(response.isStaleReferencesReset()).isTrue();
        assertThat(PostContentCodec.expandPreservedHtml(response.getContents()))
                .doesNotContain("https://staging.noviis.kr:8443/api/v1/files/99")
                .contains("https://noviis.kr/api/v1/files/99");
    }

    @Test
    void preservedViewportUploadsKeepValidFilesAndRemoveRejectedReferences() {
        file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);
        String source = "<!doctype html><html><head><style>.spark{color:red}</style></head><body>"
                + "<template data-noviis-layer=\"viewport\"><img src=\"/api/v1/files/12\">"
                + "<img src=\"/api/v1/files/99\"></template><p>Visible</p></body></html>";
        String marker = "<div class=\"noviis-sandboxed-post-html\" data-value=\""
                + Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8)) + "\"></div>";

        DraftResponse response = draftService.saveDraftPost(1L, editRequest(marker, List.of(12L, 99L)));

        assertThat(response.getFileIds()).containsExactly(12L);
        assertThat(response.isStaleReferencesReset()).isTrue();
        assertThat(response.getContents()).contains("noviis-sandboxed-post-html").doesNotContain("<template");
        assertThat(PostContentCodec.expandPreservedHtml(response.getContents()))
                .contains("/api/v1/files/12", "data-noviis-layer=\"viewport\"", ".spark{color:red}", "Visible")
                .doesNotContain("/api/v1/files/99");
    }

    @Test
    void draftReconciliationDoesNotTurnDocumentLikeCaptionTextIntoAFullDocument() {
        String source = "<!-- <html> --><p data-caption=\"<html>\">Visible</p>"
                + "<template data-noviis-layer=\"viewport\"><img src=\"/api/v1/files/99\"></template>";
        String marker = preservedHtml(source);
        DraftResponse response = draftService.saveDraftPost(1L, editRequest(marker, List.of(99L)));
        String reconciled = PostContentCodec.expandPreservedHtml(response.getContents());
        assertThat(reconciled)
                .contains("Visible", "<!-- <html> -->")
                .doesNotContain("<html><head>")
                .doesNotContain("/api/v1/files/99");
        assertThat(org.jsoup.Jsoup.parse(reconciled).selectFirst("p").attr("data-caption"))
                .isEqualTo("<html>");
    }

    @Test
    void draftReferencesIgnoreUnsupportedInertTemplates() {
        String source = "<p>Visible</p><template><img src=\"/api/v1/files/98\"></template>"
                + "<template data-noviis-layer=\"viewport\"><p>Decoration</p>"
                + "<form><img src=\"/api/v1/files/97\"></form><a href=\"/api/v1/files/96\">Ignored attachment</a>"
                + "<template><img src=\"/api/v1/files/99\"></template></template>";
        DraftResponse response = draftService.saveDraftPost(1L, editRequest(preservedHtml(source), List.of()));
        assertThat(response.getFileIds()).isEmpty();
        assertThat(response.isStaleReferencesReset()).isFalse();
        assertThat(PostContentCodec.expandPreservedHtml(response.getContents())).isEqualTo(source);
    }

    private static String preservedHtml(String source) {
        return "<div class=\"noviis-sandboxed-post-html\" data-value=\""
                + Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8)) + "\"></div>";
    }

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
    private FileService fileService;

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
            if (draft.getVersion() == null) ReflectionTestUtils.setField(draft, "version", 0L);
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
        when(fileRepository.findActiveForAssociationForUpdate(anyList(), nullable(Long.class), nullable(String.class)))
                .thenAnswer(invocation -> {
                    List<Long> requestedIds = invocation.getArgument(0);
                    Long relatedId = invocation.getArgument(1);
                    String relatedType = invocation.getArgument(2);
                    return files.stream().filter(file -> file.getStorageStatus() == FileStorageStatus.ACTIVE
                            && (requestedIds.contains(file.getFileId())
                                || (relatedId != null && file.isAssociatedWith(relatedId, relatedType))))
                            .toList();
                });
        Clock clock = Clock.systemUTC();
        FileAssociationService associationService =
                new FileAssociationService(fileRepository, mock(UserRepository.class), boardRepository, clock);
        ReflectionTestUtils.setField(associationService, "entityManager", mock(EntityManager.class));
        fileService = new FileService(
                mock(FileUploadService.class),
                associationService,
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
    void autosaveRemovalThenUndoKeepsDraftImageUntilDraftDeletion() {
        File attachment = file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);
        DraftResponse removed = draftService.saveDraftPost(1L, editRequest("<p>Removed image</p>", List.of()));

        assertThat(removed.getFileIds()).isEmpty();
        assertThat(attachment.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);

        DraftResponse restored = draftService.saveDraftPost(1L, PostDraftRequest.builder()
                .draftId(10L).version(removed.getVersion()).boardUrl("free").originalPostId(50L)
                .title("Undo image removal").contents("<img src=\"/api/v1/files/12\">")
                .fileIds(List.of(12L)).build());

        assertThat(restored.getContents()).contains("/api/v1/files/12");
        assertThat(restored.getFileIds()).containsExactly(12L);
        assertThat(restored.isStaleReferencesReset()).isFalse();
        draftService.deleteDraftPost(1L, 10L, null);
        assertThat(attachment.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
    }

    @Test
    void expirationCleansImagesOmittedFromTheCurrentDraftPayload() {
        File attachment = file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);
        DraftResponse removed = draftService.saveDraftPost(1L, editRequest("<p>Removed image</p>", List.of()));
        assertThat(removed.getFileIds()).isEmpty();
        assertThat(attachment.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        DraftPost expired = draftRepository.findByDraftIdAndUserForUpdate(10L, 1L).orElseThrow();
        when(draftRepository.findExpiredBefore(any(), any())).thenReturn(List.of(expired));

        int count = new PostDraftCleanupBatchService(draftRepository, fileService)
                .cleanupExpiredBatch(java.time.LocalDateTime.now().minusDays(90), 100);

        assertThat(count).isEqualTo(1);
        assertThat(attachment.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        verify(draftRepository).delete(expired);
    }

    @Test
    void deletionCleansImagesOmittedFromTheCurrentDraftPayload() {
        File attachment = file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);
        DraftResponse removed = draftService.saveDraftPost(1L, editRequest("<p>Removed image</p>", List.of()));
        assertThat(removed.getFileIds()).isEmpty();
        assertThat(attachment.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);

        draftService.deleteDraftPost(1L, 10L, null);

        assertThat(attachment.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savingDraftKeepsUsableEmoticonsWithoutTakingOwnership(boolean existingDraft) {
        User creator = User.builder().build();
        ReflectionTestUtils.setField(creator, "userId", 2L);
        File emoticon = file(21L, creator, 71L, FileService.RELATED_TYPE_EMOTICON_IMAGE);
        File attachment = file(12L, owner, 10L, FileService.RELATED_TYPE_DRAFT_POST);
        when(fileRepository.findUsableEmoticonImageFileIds(anyList(), eq(1L))).thenReturn(List.of(21L));
        if (existingDraft) {
            DraftPost draft = DraftPost.builder().user(owner).board(board).build();
            ReflectionTestUtils.setField(draft, "draftId", 10L);
            ReflectionTestUtils.setField(draft, "version", 2L);
            when(draftRepository.findByDraftIdAndUserForUpdate(10L, 1L)).thenReturn(Optional.of(draft));
        }
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(existingDraft ? 10L : null).version(existingDraft ? 2L : null)
                .boardUrl("free").title("Emoticons")
                .contents("<img src=\"/api/v1/files/21\" alt=\":emoticon:\"><img src=\"/api/v1/files/12\">")
                .fileIds(existingDraft ? List.of(12L) : List.of(21L, 12L)).build();

        DraftResponse response = draftService.saveDraftPost(1L, request);

        assertThat(response.getContents()).contains("/api/v1/files/21", "/api/v1/files/12");
        assertThat(response.getFileIds()).containsExactly(12L);
        assertThat(response.isStaleReferencesReset()).isFalse();
        assertThat(emoticon.isAssociatedWith(71L, FileService.RELATED_TYPE_EMOTICON_IMAGE)).isTrue();
        assertThat(emoticon.getUploader()).isSameAs(creator);

        draftService.deleteDraftPost(1L, 10L, null);
        assertThat(emoticon.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(attachment.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
    }

    @Test
    void forgedEmoticonMarkerDoesNotKeepUnauthorizedFiles() {
        User other = User.builder().build();
        ReflectionTestUtils.setField(other, "userId", 2L);
        File unrelated = file(21L, other, 90L, FileService.RELATED_TYPE_POST_CONTENT);
        File unownedEmoticon = file(22L, other, 71L, FileService.RELATED_TYPE_EMOTICON_IMAGE);
        PostDraftRequest request = PostDraftRequest.builder().boardUrl("free").title("Invalid references")
                .contents("<img src=\"/api/v1/files/21\" alt=\":emoticon:\">"
                        + "<img src=\"/api/v1/files/22\" title=\":emoticon:\">")
                .fileIds(List.of()).build();

        DraftResponse response = draftService.saveDraftPost(1L, request);

        assertThat(response.getContents()).doesNotContain("/api/v1/files/21", "/api/v1/files/22");
        assertThat(response.isStaleReferencesReset()).isTrue();
        assertThat(unrelated.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(unownedEmoticon.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
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
