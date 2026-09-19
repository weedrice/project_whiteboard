package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.actor.ActorBatchReadPort;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.integration.CommentPostIntegrationAdapter;
import com.weedrice.whiteboard.domain.comment.integration.CommentUserIntegrationAdapter;
import com.weedrice.whiteboard.domain.comment.repository.CommentMentionRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentQueryReadAccessTest {
    private static final PageRequest PAGE = PageRequest.of(0, 10);

    private PostRepository postRepository;
    private UserRepository userRepository;
    private CommentRepository commentRepository;
    private UserBlockService userBlockService;
    private CommentQueryService service;
    private Post post;

    enum ReadOperation {
        COMMENTS, BEST, REPLIES, COMMENT;

        boolean loadsPostBeforeViewer() {
            return this == COMMENTS || this == BEST;
        }
    }

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        commentRepository = mock(CommentRepository.class);
        userBlockService = mock(UserBlockService.class);
        UserReadableResolver userResolver = new UserReadableResolver(userRepository);
        InquiryLegacyWritePolicy inquiryPolicy = mock(InquiryLegacyWritePolicy.class);
        PostAccessPolicy postPolicy = new PostAccessPolicy(
                new BoardAccessPolicy(mock(AdminRepository.class)), inquiryPolicy);
        CommentPostIntegrationAdapter postPort = new CommentPostIntegrationAdapter(
                mock(EntityManager.class), postRepository, mock(BoardRepository.class), postPolicy,
                mock(PostAuthorCommandPolicy.class), inquiryPolicy, userResolver,
                mock(UserWritableResolver.class), userBlockService);
        CommentReadSupport readSupport = new CommentReadSupport(commentRepository);
        service = new CommentQueryService(
                commentRepository, inquiryPolicy, postPort,
                new CommentUserIntegrationAdapter(userResolver, userBlockService, mock(UserBlockRepository.class)),
                readSupport, new CommentReadModelAssembler(readSupport), mock(CommentMentionRepository.class),
                new StaticMessageSource(), mock(ActorBatchReadPort.class));

        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);
        Board board = Board.builder().boardName("Board").boardUrl("free").isPublic(true).build();
        ReflectionTestUtils.setField(board, "boardId", 3L);
        ReflectionTestUtils.setField(board, "isActive", true);
        post = Post.builder().user(author).board(board).title("Loaded post title").build();
        ReflectionTestUtils.setField(post, "postId", 10L);
        Comment comment = Comment.builder().user(author).post(post).depth(0).content("Comment").build();
        ReflectionTestUtils.setField(comment, "commentId", 20L);
        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of());
        when(commentRepository.findByIdWithRelations(20L)).thenReturn(Optional.of(comment));
        when(commentRepository.findNonDeletedByIdWithRelations(20L)).thenReturn(Optional.of(comment));
        when(commentRepository.findParentsWithChildrenOrNotDeleted(
                eq(10L), anyBoolean(), anyCollection(), any())).thenReturn(new PageImpl<>(List.of(comment), PAGE, 1));
        when(commentRepository.findBestRootComments(eq(10L), anyInt(), anyBoolean(), anyCollection(), any()))
                .thenReturn(List.of(comment));
        when(commentRepository.findRepliesWithRelations(eq(20L), eq(false), anyBoolean(), anyCollection(), any()))
                .thenReturn(new PageImpl<>(List.of(comment), PAGE, 1));
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void authenticatedRead_loadsPostOnceAndPreservesLookupOrder(ReadOperation operation) {
        CommentResponse response = read(operation, 2L);

        assertThat(response.getPostTitle()).isEqualTo("Loaded post title");
        verify(postRepository).findByIdWithRelations(10L);
        verify(postRepository, never()).findByIdWithRelationsForUpdate(anyLong());
        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(2L);
        InOrder order = inOrder(postRepository, userRepository);
        if (operation.loadsPostBeforeViewer()) {
            order.verify(postRepository).findByIdWithRelations(10L);
            order.verify(userRepository, org.mockito.Mockito.times(2)).findById(2L);
        } else {
            order.verify(userRepository).findById(2L);
            order.verify(postRepository).findByIdWithRelations(10L);
            order.verify(userRepository).findById(2L);
        }
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void anonymousRead_loadsPostOnce(ReadOperation operation) {
        assertThat(read(operation, null).getPostTitle()).isEqualTo("Loaded post title");

        verify(postRepository).findByIdWithRelations(10L);
        verify(userRepository, never()).findById(anyLong());
        verify(userBlockService, never()).getBlockedUserIdsEitherDirectionForExistingUser(anyLong());
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void missingPostAndViewer_preserveErrorPriority(ReadOperation operation) {
        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertReadError(operation, 2L,
                operation.loadsPostBeforeViewer() ? ErrorCode.POST_NOT_FOUND : ErrorCode.USER_NOT_FOUND);

        if (operation.loadsPostBeforeViewer()) {
            verify(userRepository, never()).findById(anyLong());
        } else {
            verify(postRepository, never()).findByIdWithRelations(anyLong());
        }
    }

    @ParameterizedTest
    @EnumSource(value = ReadOperation.class, names = {"REPLIES", "COMMENT"})
    void missingComment_isCheckedBeforeViewerAndPost(ReadOperation operation) {
        when(commentRepository.findByIdWithRelations(20L)).thenReturn(Optional.empty());
        when(commentRepository.findNonDeletedByIdWithRelations(20L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.empty());

        assertReadError(operation, 2L, ErrorCode.COMMENT_NOT_FOUND);

        verify(userRepository, never()).findById(anyLong());
        verify(postRepository, never()).findByIdWithRelations(anyLong());
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void missingViewer_isCheckedBeforeUnreadablePost(ReadOperation operation) {
        ReflectionTestUtils.setField(post, "isDeleted", true);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertReadError(operation, 2L, ErrorCode.USER_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void blockedPostAuthor_remainsHidden(ReadOperation operation) {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));

        assertReadError(operation, 2L, ErrorCode.POST_NOT_FOUND);

        verify(postRepository).findByIdWithRelations(10L);
        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(2L);
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void privateBoard_remainsHiddenFromAnonymousViewer(ReadOperation operation) {
        ReflectionTestUtils.setField(post.getBoard(), "isPublic", false);

        assertReadError(operation, null, ErrorCode.POST_NOT_FOUND);

        verify(postRepository).findByIdWithRelations(10L);
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void secretPost_remainsHiddenFromOtherUsers(ReadOperation operation) {
        ReflectionTestUtils.setField(post, "isSecret", true);

        assertReadError(operation, 2L, ErrorCode.POST_NOT_FOUND);

        verify(postRepository).findByIdWithRelations(10L);
    }

    private void assertReadError(ReadOperation operation, Long viewerId, ErrorCode code) {
        assertThatThrownBy(() -> read(operation, viewerId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", code);
    }

    private CommentResponse read(ReadOperation operation, Long viewerId) {
        return switch (operation) {
            case COMMENTS -> service.getComments(10L, viewerId, PAGE).getContent().getFirst();
            case BEST -> service.getBestComments(10L, viewerId).getFirst();
            case REPLIES -> service.getReplies(20L, viewerId, PAGE).getContent().getFirst();
            case COMMENT -> service.getComment(20L, viewerId);
        };
    }
}
