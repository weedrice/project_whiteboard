package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedGenerationServiceTest {

    @InjectMocks
    private FeedGenerationService feedGenerationService;

    @Mock
    private UserFeedRepository userFeedRepository;

    @Mock
    private PostRepository postRepository;

    private Board board;
    private User firstUser;
    private Post post;

    @BeforeEach
    void setUp() {
        board = Board.builder().boardName("free").build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        firstUser = User.builder().loginId("user1").build();
        ReflectionTestUtils.setField(firstUser, "userId", 1L);

        post = Post.builder()
                .user(firstUser)
                .board(board)
                .title("title")
                .contents("contents")
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);
    }

    @Test
    @DisplayName("게시글 발행 후 구독자 피드를 bulk insert 한다")
    void generatePostFeeds_bulkInsertsSubscriberFeeds() {
        when(postRepository.findByIdForUpdate(100L)).thenReturn(java.util.Optional.of(post));

        feedGenerationService.generatePostFeeds(board, 100L);

        verify(postRepository).findByIdForUpdate(100L);
        verify(userFeedRepository).insertSubscriptionPostFeeds(
                10L,
                FeedGenerationService.FEED_TYPE_SUBSCRIPTION_POST,
                FeedGenerationService.CONTENT_TYPE_POST,
                100L,
                FeedGenerationService.SOURCE_CRITERIA_BOARD_SUBSCRIPTION,
                10L);
    }

    @Test
    @DisplayName("게시글을 찾지 못하면 구독자 피드를 생성하지 않는다")
    void generatePostFeeds_postNotFoundDoesNotInsertFeeds() {
        when(postRepository.findByIdForUpdate(100L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> feedGenerationService.generatePostFeeds(board, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(userFeedRepository, never()).insertSubscriptionPostFeeds(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }
}
