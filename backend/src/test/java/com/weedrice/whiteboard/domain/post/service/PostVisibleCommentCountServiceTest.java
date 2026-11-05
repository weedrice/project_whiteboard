package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.comment.query.CommentVisibleCountReader;
import com.weedrice.whiteboard.domain.post.port.PostUserReadPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostVisibleCommentCountServiceTest {
    private final CommentVisibleCountReader reader = mock(CommentVisibleCountReader.class);
    private final PostUserReadPort users = mock(PostUserReadPort.class);
    private final PostVisibleCommentCountService service = new PostVisibleCommentCountService(reader, users);

    @Test
    void authenticatedViewerUsesEitherDirectionBlocksInOneBatch() {
        when(users.getBlockedUserIdsForExistingUser(7L)).thenReturn(List.of(8L, 9L));
        when(reader.countByPostIds(List.of(1L, 2L), List.of(8L, 9L))).thenReturn(Map.of(1L, 3));

        assertThat(service.countByPostIds(List.of(1L, 2L), 7L)).containsExactlyEntriesOf(Map.of(1L, 3));

        verify(users).getBlockedUserIdsForExistingUser(7L);
        verify(reader).countByPostIds(List.of(1L, 2L), List.of(8L, 9L));
        verifyNoMoreInteractions(reader, users);
    }

    @Test
    void anonymousViewerUsesNoBlocksAndEmptyPagesDoNotQuery() {
        when(reader.countByPostIds(List.of(1L), List.of())).thenReturn(Map.of());

        assertThat(service.countByPostIds(List.of(1L), null)).isEmpty();
        assertThat(service.countByPostIds(List.of(), 7L)).isEmpty();
        assertThat(service.countByPostIds(null, 7L)).isEmpty();

        verify(reader).countByPostIds(List.of(1L), List.of());
        verifyNoInteractions(users);
        verifyNoMoreInteractions(reader);
    }
}
