package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedGenerationServiceTest {

    @InjectMocks
    private FeedGenerationService feedGenerationService;

    @Mock
    private BoardSubscriptionRepository boardSubscriptionRepository;

    @Mock
    private UserFeedRepository userFeedRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserFeedCommandService userFeedCommandService;

    private Board board;
    private User firstUser;
    private User secondUser;
    private Post post;

    @BeforeEach
    void setUp() {
        board = Board.builder().boardName("free").build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        firstUser = User.builder().loginId("user1").build();
        secondUser = User.builder().loginId("user2").build();
        ReflectionTestUtils.setField(firstUser, "userId", 1L);
        ReflectionTestUtils.setField(secondUser, "userId", 2L);

        post = Post.builder()
                .user(firstUser)
                .board(board)
                .title("title")
                .contents("contents")
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);
    }

    @Test
    @DisplayName("게시글 발행 시 게시판 구독자 피드를 생성한다")
    void generatePostFeeds_createsFeedsForSubscribers() {
        BoardSubscription firstSubscription = BoardSubscription.builder().user(firstUser).board(board).role("MEMBER").build();
        BoardSubscription secondSubscription = BoardSubscription.builder().user(secondUser).board(board).role("MEMBER").build();

        when(postRepository.findByIdForUpdate(100L)).thenReturn(java.util.Optional.of(post));
        when(boardSubscriptionRepository.findAllByBoard(board)).thenReturn(List.of(firstSubscription, secondSubscription));
        when(userFeedRepository.findExistingTargetUserIds(List.of(1L, 2L),
                FeedGenerationService.FEED_TYPE_SUBSCRIPTION_POST,
                FeedGenerationService.CONTENT_TYPE_POST,
                100L,
                FeedGenerationService.SOURCE_CRITERIA_BOARD_SUBSCRIPTION,
                10L)).thenReturn(List.of());

        feedGenerationService.generatePostFeeds(board, 100L);

        ArgumentCaptor<UserFeed> captor = ArgumentCaptor.forClass(UserFeed.class);
        verify(postRepository).findByIdForUpdate(100L);
        verify(userFeedCommandService, org.mockito.Mockito.times(2)).saveFeedIfAbsent(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues()).extracting(UserFeed::getTargetUser).containsExactly(firstUser, secondUser);
        assertThat(captor.getAllValues()).extracting(UserFeed::getContentId).containsOnly(100L);
        assertThat(captor.getAllValues()).extracting(UserFeed::getContentType)
                .containsOnly(FeedGenerationService.CONTENT_TYPE_POST);
    }

    @Test
    @DisplayName("동일 이벤트를 다시 처리해도 기존 피드는 중복 생성하지 않는다")
    void generatePostFeeds_skipsExistingSubscribers() {
        BoardSubscription firstSubscription = BoardSubscription.builder().user(firstUser).board(board).role("MEMBER").build();
        BoardSubscription secondSubscription = BoardSubscription.builder().user(secondUser).board(board).role("MEMBER").build();

        when(postRepository.findByIdForUpdate(100L)).thenReturn(java.util.Optional.of(post));
        when(boardSubscriptionRepository.findAllByBoard(board)).thenReturn(List.of(firstSubscription, secondSubscription));
        when(userFeedRepository.findExistingTargetUserIds(List.of(1L, 2L),
                FeedGenerationService.FEED_TYPE_SUBSCRIPTION_POST,
                FeedGenerationService.CONTENT_TYPE_POST,
                100L,
                FeedGenerationService.SOURCE_CRITERIA_BOARD_SUBSCRIPTION,
                10L)).thenReturn(List.of(1L, 2L));

        feedGenerationService.generatePostFeeds(board, 100L);

        verify(userFeedCommandService, never()).saveFeedIfAbsent(org.mockito.ArgumentMatchers.any(UserFeed.class));
    }

    @Test
    @DisplayName("구독 목록에 같은 사용자가 중복되어도 피드는 한 번만 생성한다")
    void generatePostFeeds_deduplicatesSubscriberUsers() {
        BoardSubscription firstSubscription = BoardSubscription.builder().user(firstUser).board(board).role("MEMBER").build();
        BoardSubscription duplicateSubscription = BoardSubscription.builder().user(firstUser).board(board).role("MODERATOR").build();

        when(postRepository.findByIdForUpdate(100L)).thenReturn(java.util.Optional.of(post));
        when(boardSubscriptionRepository.findAllByBoard(board)).thenReturn(List.of(firstSubscription, duplicateSubscription));
        when(userFeedRepository.findExistingTargetUserIds(List.of(1L),
                FeedGenerationService.FEED_TYPE_SUBSCRIPTION_POST,
                FeedGenerationService.CONTENT_TYPE_POST,
                100L,
                FeedGenerationService.SOURCE_CRITERIA_BOARD_SUBSCRIPTION,
                10L)).thenReturn(List.of());

        feedGenerationService.generatePostFeeds(board, 100L);

        ArgumentCaptor<UserFeed> captor = ArgumentCaptor.forClass(UserFeed.class);
        verify(userFeedCommandService).saveFeedIfAbsent(captor.capture());
        assertThat(captor.getValue().getTargetUser()).isEqualTo(firstUser);
    }
}
