package com.weedrice.whiteboard.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostSeriesNavigation;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import com.weedrice.whiteboard.domain.post.entity.PostSeriesItem;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesItemRepository;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostSeriesServiceTest {

    @Mock
    private PostSeriesRepository postSeriesRepository;

    @Mock
    private PostSeriesItemRepository postSeriesItemRepository;

    @Mock
    private UserWritableResolver userWritableResolver;

    @Test
    void getNavigation_includesCurrentIndexAndTotalCount() {
        PostSeriesService service = new PostSeriesService(
                postSeriesRepository,
                postSeriesItemRepository,
                userWritableResolver);
        User owner = createUser(1L);
        Board board = createBoard("free");
        PostSeries series = createSeries(10L, owner, "Series title");
        Post first = createPost(100L, board, owner, "First");
        Post current = createPost(101L, board, owner, "Current");
        Post next = createPost(102L, board, owner, "Next");
        PostSeriesItem firstItem = createItem(1L, series, first, 0);
        PostSeriesItem currentItem = createItem(2L, series, current, 1);
        PostSeriesItem nextItem = createItem(3L, series, next, 2);

        when(postSeriesItemRepository.findByPost_PostId(101L)).thenReturn(Optional.of(currentItem));
        when(postSeriesItemRepository.findBySeries_SeriesIdOrderBySortOrderAscItemIdAsc(10L))
                .thenReturn(List.of(firstItem, currentItem, nextItem));

        PostSeriesNavigation navigation = service.getNavigation(current);

        assertThat(navigation.getSeries().getSeriesId()).isEqualTo(10L);
        assertThat(navigation.getSeries().getTitle()).isEqualTo("Series title");
        assertThat(navigation.getSeries().getCurrentIndex()).isEqualTo(2);
        assertThat(navigation.getSeries().getTotalCount()).isEqualTo(3);
        assertThat(navigation.getPreviousPost().getPostId()).isEqualTo(100L);
        assertThat(navigation.getNextPost().getPostId()).isEqualTo(102L);
    }

    private User createUser(Long userId) {
        User user = User.builder()
                .loginId("user" + userId)
                .displayName("User " + userId)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Board createBoard(String boardUrl) {
        Board board = Board.builder()
                .boardName("Board")
                .boardUrl(boardUrl)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        return board;
    }

    private PostSeries createSeries(Long seriesId, User owner, String title) {
        PostSeries series = PostSeries.builder()
                .owner(owner)
                .title(title)
                .build();
        ReflectionTestUtils.setField(series, "seriesId", seriesId);
        return series;
    }

    private Post createPost(Long postId, Board board, User user, String title) {
        Post post = Post.builder()
                .title(title)
                .contents("content")
                .board(board)
                .user(user)
                .build();
        ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }

    private PostSeriesItem createItem(Long itemId, PostSeries series, Post post, Integer sortOrder) {
        PostSeriesItem item = PostSeriesItem.builder()
                .series(series)
                .post(post)
                .sortOrder(sortOrder)
                .build();
        ReflectionTestUtils.setField(item, "itemId", itemId);
        return item;
    }
}
