package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentBoardPostCountIntegrationAdapterTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final AgentBoardPostCountIntegrationAdapter adapter = new AgentBoardPostCountIntegrationAdapter(posts);

    @Test
    void emptyBoardsSkipBothCountQueries() {
        assertThat(adapter.countVisiblePostsByBoardIds(List.of(), Set.of(20L))).isEmpty();
        verifyNoInteractions(posts);
    }

    @Test
    void ordinaryBoardsUsePublicCountsInOneBatchAndLeaveMissingRowsAbsent() {
        when(posts.countPublicVisiblePostsByBoardIds(List.of(10L, 11L)))
                .thenReturn(List.of(count(10L, 3L)));

        assertThat(adapter.countVisiblePostsByBoardIds(List.of(10L, 11L), Set.of()))
                .isEqualTo(Map.of(10L, 3L));
        verify(posts).countPublicVisiblePostsByBoardIds(List.of(10L, 11L));
        verifyNoMoreInteractions(posts);
    }

    @Test
    void managedBoardsKeepOriginalActiveCountsInOneBatch() {
        when(posts.countActiveByBoardIds(List.of(10L, 20L))).thenReturn(List.of(count(10L, 7L), count(20L, 9L)));

        assertThat(adapter.countVisiblePostsByBoardIds(List.of(10L, 20L), Set.of(10L, 20L)))
                .isEqualTo(Map.of(10L, 7L, 20L, 9L));
        verify(posts).countActiveByBoardIds(List.of(10L, 20L));
        verifyNoMoreInteractions(posts);
    }

    @Test
    void mixedPermissionsSplitOnlyRequestedDistinctIdsAcrossTwoBatches() {
        when(posts.countPublicVisiblePostsByBoardIds(List.of(10L, 11L))).thenReturn(List.of(count(10L, 2L)));
        when(posts.countActiveByBoardIds(List.of(20L, 21L))).thenReturn(List.of(count(20L, 8L), count(21L, 9L)));

        assertThat(adapter.countVisiblePostsByBoardIds(List.of(10L, 20L, 11L, 21L, 10L), Set.of(20L, 21L, 99L)))
                .isEqualTo(Map.of(10L, 2L, 20L, 8L, 21L, 9L));
        verify(posts).countPublicVisiblePostsByBoardIds(List.of(10L, 11L));
        verify(posts).countActiveByBoardIds(List.of(20L, 21L));
        verifyNoMoreInteractions(posts);
    }

    private PostRepository.BoardPostCountProjection count(Long boardId, long postCount) {
        return new PostRepository.BoardPostCountProjection() {
            @Override public Long getBoardId() { return boardId; }
            @Override public Long getPostCount() { return postCount; }
        };
    }
}
