package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentObservedActivityBoundaryTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final BoardRepository boards = mock(BoardRepository.class);
    private final UserBlockService blocks = mock(UserBlockService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AgentPostActivityContentIntegrationAdapter adapter =
            new AgentPostActivityContentIntegrationAdapter(posts, comments, boards, blocks, entityManager);
    private final LocalDateTime at = LocalDateTime.of(2026, 11, 5, 12, 0);
    private Board board;
    private Post post;
    private Comment observed;

    @BeforeEach
    void setup() {
        board = Board.builder().boardName("Public").boardUrl("public").build();
        ReflectionTestUtils.setField(board, "boardId", 9L);
        post = Post.builder().title("Owned").contents("body").board(board).userId(1L).agentId(7L).build();
        ReflectionTestUtils.setField(post, "postId", 10L);
        observed = Comment.builder().postId(10L).userId(2L).content("Read content").depth(0).build();
        ReflectionTestUtils.setField(observed, "commentId", 11L);
        ReflectionTestUtils.setField(observed, "createdAt", at);
        when(posts.findBoardIdByPostId(10L)).thenReturn(Optional.of(9L));
        when(boards.findByIdForUpdate(9L)).thenReturn(Optional.of(board));
        when(posts.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(post));
        when(blocks.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(99L));
        when(comments.findAllById(Set.of(11L))).thenReturn(List.of(observed));
    }

    @Test
    void acknowledgesAllObservedTimestampAndLocksBeforeReading() {
        assertThat(adapter.resolveObservedBoundary(7L, 10L, List.of(11L, 11L))).isEqualTo(at);
        InOrder order = inOrder(boards, posts, entityManager, comments);
        order.verify(posts).findBoardIdByPostId(10L);
        order.verify(boards).findByIdForUpdate(9L);
        order.verify(entityManager).refresh(board);
        order.verify(posts).findByIdWithRelationsForUpdate(10L);
        order.verify(entityManager).refresh(post);
        order.verify(comments).findAllById(Set.of(11L));
        order.verify(comments).findFirstUnobservedActivityAt(7L, 10L, false, List.of(99L), at, Set.of(11L));
    }

    @Test
    void unseenPeerWithSameTimestampKeepsWholeGroupUnread() {
        when(comments.findFirstUnobservedActivityAt(7L, 10L, false, List.of(99L), at, Set.of(11L)))
                .thenReturn(Optional.of(at));
        assertThat(adapter.resolveObservedBoundary(7L, 10L, List.of(11L))).isEqualTo(at.minusNanos(1000));
    }

    @Test
    void earlierUnobservedPageOrReplyStopsPrefixAdvancement() {
        LocalDateTime gap = at.minusMinutes(5);
        when(comments.findFirstUnobservedActivityAt(7L, 10L, false, List.of(99L), at, Set.of(11L)))
                .thenReturn(Optional.of(gap));
        assertThat(adapter.resolveObservedBoundary(7L, 10L, List.of(11L))).isEqualTo(gap.minusNanos(1000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "blinded", "blocked", "other_post", "missing", "timestamp"})
    void rejectsInvisibleOrForeignObservedIds(String reason) {
        switch (reason) {
            case "deleted" -> observed.deleteComment();
            case "blinded" -> observed.blind("moderated", at);
            case "blocked" -> ReflectionTestUtils.setField(observed, "userId", 99L);
            case "other_post" -> ReflectionTestUtils.setField(observed, "postId", 20L);
            case "missing" -> when(comments.findAllById(Set.of(11L))).thenReturn(List.of());
            case "timestamp" -> ReflectionTestUtils.setField(observed, "createdAt", null);
        }
        assertThatThrownBy(() -> adapter.resolveObservedBoundary(7L, 10L, List.of(11L)))
                .isInstanceOf(BusinessException.class);
        verify(comments, never()).findFirstUnobservedActivityAt(any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void rejectsUnboundedOrEmptyInputBeforeLocks() {
        assertThatThrownBy(() -> adapter.resolveObservedBoundary(7L, 10L, List.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> adapter.resolveObservedBoundary(7L, 10L, java.util.Collections.nCopies(1001, 11L)))
                .isInstanceOf(BusinessException.class);
        verify(posts, never()).findBoardIdByPostId(any());
    }
}
