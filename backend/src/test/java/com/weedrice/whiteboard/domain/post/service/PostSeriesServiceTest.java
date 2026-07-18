package com.weedrice.whiteboard.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
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
import java.util.Set;
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

    @Mock
    private PostReadContextResolver postReadContextResolver;

    @Test
    void getNavigation_includesCurrentIndexAndTotalCount() {
        PostSeriesService service = new PostSeriesService(
                postSeriesRepository,
                postSeriesItemRepository,
                userWritableResolver,
                postReadContextResolver,
                new PostAccessPolicy(mock(BoardAccessPolicy.class)));
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
        PostReadContext context = PostReadContext.anonymous();
        when(postReadContextResolver.withAdminBoardIdsForPosts(eq(context), anyList())).thenReturn(context);

        PostSeriesNavigation navigation = service.getNavigation(current, context);

        assertThat(navigation.getSeries().getSeriesId()).isEqualTo(10L);
        assertThat(navigation.getSeries().getTitle()).isEqualTo("Series title");
        assertThat(navigation.getSeries().getCurrentIndex()).isEqualTo(2);
        assertThat(navigation.getSeries().getTotalCount()).isEqualTo(3);
        assertThat(navigation.getPreviousPost().getPostId()).isEqualTo(100L);
        assertThat(navigation.getNextPost().getPostId()).isEqualTo(102L);
    }

    @Test
    void getNavigation_excludesUnreadableItemsWithoutLeakingMetadata() {
        PostSeriesService service = new PostSeriesService(
                postSeriesRepository,
                postSeriesItemRepository,
                userWritableResolver,
                postReadContextResolver,
                new PostAccessPolicy(mock(BoardAccessPolicy.class)));
        User owner = createUser(1L);
        User viewer = createUser(2L);
        User blockedAuthor = createUser(3L);
        Board publicBoard = createBoard("free");
        Board privateBoard = createBoard("private");
        ReflectionTestUtils.setField(privateBoard, "isPublic", false);
        PostSeries series = createSeries(10L, owner, "Series title");
        Post first = createPost(100L, publicBoard, owner, "First");
        Post deleted = createPost(101L, publicBoard, owner, "Deleted title");
        deleted.deletePost();
        Post blinded = createPost(102L, publicBoard, owner, "Blinded title");
        blinded.blind("reported", java.time.LocalDateTime.now());
        Post secret = createPost(103L, publicBoard, owner, "Secret title");
        ReflectionTestUtils.setField(secret, "isSecret", true);
        Post privatePost = createPost(104L, privateBoard, owner, "Private title");
        Post blocked = createPost(105L, publicBoard, blockedAuthor, "Blocked title");
        Post current = createPost(106L, publicBoard, owner, "Current");
        Post next = createPost(107L, publicBoard, owner, "Next");
        List<PostSeriesItem> items = List.of(
                createItem(1L, series, first, 0),
                createItem(2L, series, deleted, 1),
                createItem(3L, series, blinded, 2),
                createItem(4L, series, secret, 3),
                createItem(5L, series, privatePost, 4),
                createItem(6L, series, blocked, 5),
                createItem(7L, series, current, 6),
                createItem(8L, series, next, 7));
        PostReadContext context = new PostReadContext(viewer, 2L, List.of(3L), Set.of(3L), Set.of());

        when(postSeriesItemRepository.findByPost_PostId(106L)).thenReturn(Optional.of(items.get(6)));
        when(postSeriesItemRepository.findBySeries_SeriesIdOrderBySortOrderAscItemIdAsc(10L)).thenReturn(items);
        when(postReadContextResolver.withAdminBoardIdsForPosts(eq(context), anyList())).thenReturn(context);

        PostSeriesNavigation navigation = service.getNavigation(current, context);

        assertThat(navigation.getSeries().getCurrentIndex()).isEqualTo(2);
        assertThat(navigation.getSeries().getTotalCount()).isEqualTo(3);
        assertThat(navigation.getPreviousPost().getPostId()).isEqualTo(100L);
        assertThat(navigation.getNextPost().getPostId()).isEqualTo(107L);
    }

    @Test
    void attachPostToSeries_locksSeriesBeforeAllocatingNextOrder() {
        PostSeriesService service = new PostSeriesService(
                postSeriesRepository,
                postSeriesItemRepository,
                userWritableResolver,
                postReadContextResolver,
                mock(PostAccessPolicy.class));
        User owner = createUser(1L);
        PostSeries series = createSeries(10L, owner, "Series title");
        Post post = createPost(100L, createBoard("free"), owner, "Post");
        when(postSeriesRepository.findBySeriesIdAndOwnerUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(series));
        when(postSeriesItemRepository.findByPost_PostIdAndSeries_Owner_UserId(100L, 1L))
                .thenReturn(Optional.empty());
        when(postSeriesItemRepository.findMaxSortOrder(10L)).thenReturn(4);

        service.attachPostToSeries(1L, post, 10L);

        verify(postSeriesRepository).findBySeriesIdAndOwnerUserIdForUpdate(10L, 1L);
        verify(postSeriesItemRepository).save(org.mockito.ArgumentMatchers.argThat(item ->
                item.getSeries() == series && item.getPost() == post && item.getSortOrder() == 5));
    }

    @Test
    void attachPostToSeries_sameSeries_keepsExistingOrderWithoutReallocation() {
        PostSeriesService service = new PostSeriesService(
                postSeriesRepository,
                postSeriesItemRepository,
                userWritableResolver,
                postReadContextResolver,
                mock(PostAccessPolicy.class));
        User owner = createUser(1L);
        PostSeries series = createSeries(10L, owner, "Series title");
        Post post = createPost(100L, createBoard("free"), owner, "Post");
        PostSeriesItem item = createItem(1L, series, post, 3);
        when(postSeriesRepository.findBySeriesIdAndOwnerUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(series));
        when(postSeriesItemRepository.findByPost_PostIdAndSeries_Owner_UserId(100L, 1L))
                .thenReturn(Optional.of(item));

        service.attachPostToSeries(1L, post, 10L);

        assertThat(item.getSortOrder()).isEqualTo(3);
        verify(postSeriesItemRepository, never()).findMaxSortOrder(10L);
        verify(postSeriesItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatePostSeries_withExplicitNull_removesExistingAssignment() {
        PostSeriesService service = new PostSeriesService(
                postSeriesRepository,
                postSeriesItemRepository,
                userWritableResolver,
                postReadContextResolver,
                mock(PostAccessPolicy.class));
        User owner = createUser(1L);
        Post post = createPost(100L, createBoard("free"), owner, "Post");
        PostSeriesItem item = createItem(1L, createSeries(10L, owner, "Series"), post, 0);
        when(postSeriesItemRepository.findByPost_PostIdAndSeries_Owner_UserId(100L, 1L))
                .thenReturn(Optional.of(item));

        service.updatePostSeries(1L, post, null);

        verify(postSeriesItemRepository).delete(item);
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
