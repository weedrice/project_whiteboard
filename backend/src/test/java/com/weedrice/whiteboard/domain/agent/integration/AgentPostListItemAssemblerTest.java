package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.actor.ActorBatchReadPort;
import com.weedrice.whiteboard.domain.actor.AuthorSnapshot;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostVisibleCommentCountService;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentPostListItemAssemblerTest {
    private final CommentRepository comments = mock(CommentRepository.class);
    private final ActorBatchReadPort actors = mock(ActorBatchReadPort.class);
    private final PostVisibleCommentCountService counts = mock(PostVisibleCommentCountService.class);
    private final AgentPostListItemAssembler assembler = new AgentPostListItemAssembler(comments, actors, counts);

    @Test
    void batchesViewerCountsAndUsesZeroForMissingRowsWithoutChangingStatistics() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 5L);
        Board board = Board.builder().boardName("Free").boardUrl("free").creator(author).build();
        Post first = post(100L, author, board);
        Post second = post(101L, author, board);
        var authorRef = new ContentActorRef(5L, null);
        when(actors.resolveAuthors(Set.of(authorRef))).thenReturn(Map.of(
                authorRef, new AuthorSnapshot(5L, null, "USER", "Author", null, null)));
        when(counts.countByPostIds(List.of(100L, 101L), 1L)).thenReturn(Map.of(100L, 3));
        when(comments.findDistinctPostIdsByPostIdInAndAgentIdAndIsDeletedFalse(List.of(100L, 101L), 7L))
                .thenReturn(List.of(101L));
        var page = new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 23);

        var result = assembler.fromPosts(page, 7L, 1L);

        assertThat(result.getContent()).extracting(AgentPostListItem::getCommentCount).containsExactly(3, 0);
        assertThat(result.getContent()).extracting(AgentPostListItem::isHasMyComment).containsExactly(false, true);
        assertThat(result.getTotalElements()).isEqualTo(23);
        assertThat(first.getCommentCount()).isEqualTo(12);
        assertThat(second.getCommentCount()).isEqualTo(12);
        verify(counts).countByPostIds(List.of(100L, 101L), 1L);
        verifyNoMoreInteractions(counts);
    }

    @Test
    void emptyPagePreservesTotalWithoutCountQueries() {
        var result = assembler.fromPosts(new PageImpl<>(List.of(), PageRequest.of(4, 10), 23), 7L, 1L);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(23);
        verifyNoInteractions(comments, actors, counts);
    }

    private Post post(Long id, User author, Board board) {
        Post post = Post.builder().user(author).board(board).title("Post " + id).contents("Body").build();
        ReflectionTestUtils.setField(post, "postId", id);
        ReflectionTestUtils.setField(post, "commentCount", 12);
        return post;
    }
}
