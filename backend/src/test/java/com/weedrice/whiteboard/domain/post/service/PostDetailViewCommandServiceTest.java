package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDetailViewCommandServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private ViewHistoryRepository viewHistoryRepository;

    @Mock
    private ViewHistoryCommandService viewHistoryCommandService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBlockService userBlockService;

    @Mock
    private AdminRepository adminRepository;

    private PostDetailViewCommandService commandService;

    private User user;
    private Board board;
    private Post post;

    @BeforeEach
    void setUp() {
        BoardAccessPolicy boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        PostReadContextResolver postReadContextResolver =
                new PostReadContextResolver(userRepository, userBlockService, adminRepository);
        PostAccessPolicy postAccessPolicy = new PostAccessPolicy(boardAccessPolicy);
        PostDetailContextResolver postDetailContextResolver = new PostDetailContextResolver(
                postRepository,
                viewHistoryRepository,
                postReadContextResolver,
                postAccessPolicy);
        PostViewCountWriter postViewCountWriter = new PostViewCountWriter(postRepository);
        commandService = new PostDetailViewCommandService(
                postRepository,
                viewHistoryCommandService,
                postDetailContextResolver,
                postViewCountWriter);

        user = User.builder().loginId("reader").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        board = Board.builder().boardName("board").creator(user).build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        post = Post.builder().title("title").contents("body").user(user).board(board).build();
        ReflectionTestUtils.setField(post, "postId", 100L);
        ReflectionTestUtils.setField(post, "isDeleted", false);
        ReflectionTestUtils.setField(post, "isSecret", false);
    }

    @Test
    @DisplayName("로그인 사용자의 읽기 가능한 상세 조회는 조회수를 증가시키고 조회 기록을 갱신한다")
    void recordReadableView_existingUser_incrementsViewAndTouchesHistory() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(postRepository.incrementViewCount(100L)).thenReturn(1);
        when(postRepository.findViewCountByPostId(100L)).thenReturn(11);

        int viewCount = commandService.recordReadableView(100L, 1L);

        assertThat(viewCount).isEqualTo(11);
        verify(viewHistoryCommandService).touchView(user, post);
    }

    @Test
    @DisplayName("익명 사용자의 읽기 가능한 상세 조회는 조회 기록을 갱신하지 않는다")
    void recordReadableView_anonymousUser_doesNotTouchHistory() {
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(postRepository.incrementViewCount(100L)).thenReturn(1);
        when(postRepository.findViewCountByPostId(100L)).thenReturn(11);

        int viewCount = commandService.recordReadableView(100L, null);

        assertThat(viewCount).isEqualTo(11);
        verifyNoInteractions(viewHistoryCommandService);
    }

    @Test
    @DisplayName("읽을 수 없는 게시글은 조회수와 조회 기록을 갱신하지 않는다")
    void recordReadableView_unreadablePost_doesNotIncrementView() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.singletonList(1L));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> commandService.recordReadableView(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(postRepository, never()).incrementViewCount(100L);
        verifyNoInteractions(viewHistoryCommandService);
    }

    @Test
    @DisplayName("조회수 증가 대상이 없으면 조회 기록 갱신 전에 게시글 없음 예외를 반환한다")
    void recordReadableView_incrementCountZero_throwsPostNotFoundBeforeHistoryTouch() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(postRepository.incrementViewCount(100L)).thenReturn(0);

        assertThatThrownBy(() -> commandService.recordReadableView(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verifyNoInteractions(viewHistoryCommandService);
        verify(postRepository, never()).findViewCountByPostId(100L);
    }

    @Test
    @DisplayName("증가 후 조회수를 재조회할 수 없으면 게시글 없음 예외를 반환한다")
    void recordReadableView_refreshedViewCountMissing_throwsPostNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(postRepository.incrementViewCount(100L)).thenReturn(1);
        when(postRepository.findViewCountByPostId(100L)).thenReturn(null);

        assertThatThrownBy(() -> commandService.recordReadableView(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(viewHistoryCommandService).touchView(user, post);
    }
}
