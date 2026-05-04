package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostInteractionContextResolverTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private ScrapRepository scrapRepository;
    @Mock
    private BoardSubscriptionRepository boardSubscriptionRepository;

    private PostInteractionContextResolver resolver;

    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        resolver = new PostInteractionContextResolver(
                userRepository,
                postLikeRepository,
                scrapRepository,
                boardSubscriptionRepository);

        user = User.builder().displayName("user").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        post = Post.builder()
                .title("title")
                .contents("contents")
                .user(user)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);
    }

    @Test
    @DisplayName("로그인 사용자의 상호작용 컨텍스트를 한번에 조합한다")
    void resolve_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postLikeRepository.findByUserAndPostIn(user, List.of(post)))
                .thenReturn(List.of(PostLike.builder().user(user).post(post).build()));
        when(scrapRepository.findByUserAndPostIn(user, List.of(post)))
                .thenReturn(List.of(Scrap.builder().user(user).post(post).build()));
        when(boardSubscriptionRepository.findByUserAndBoardIn(user, List.of(post.getBoard())))
                .thenReturn(List.of(BoardSubscription.builder().user(user).board(post.getBoard()).build()));

        PostUserInteractionContext context = resolver.resolve(List.of(post), 1L);

        assertThat(context.likedPostIds()).containsExactly(100L);
        assertThat(context.scrappedPostIds()).containsExactly(100L);
        assertThat(context.subscribedBoardUrls()).containsExactly("free");
    }

    @Test
    @DisplayName("익명 사용자는 빈 상호작용 컨텍스트를 반환한다")
    void resolve_anonymous_returnsEmptyContext() {
        PostUserInteractionContext context = resolver.resolve(List.of(post), null);

        assertThat(context).isEqualTo(PostUserInteractionContext.empty());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("사용자 ID가 있지만 존재하지 않으면 USER_NOT_FOUND를 던진다")
    void resolve_missingUser_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(List.of(post), 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글이 없어도 사용자 ID가 존재하지 않으면 USER_NOT_FOUND를 던진다")
    void resolve_emptyPostsAndMissingUser_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(List.of(), 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
