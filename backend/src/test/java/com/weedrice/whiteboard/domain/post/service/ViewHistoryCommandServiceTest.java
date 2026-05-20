package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewHistoryCommandServiceTest {

    @InjectMocks
    private ViewHistoryCommandService viewHistoryCommandService;

    @Mock
    private ViewHistoryRepository viewHistoryRepository;

    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("user")
                .email("user@test.com")
                .password("password")
                .displayName("User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder()
                .boardName("board")
                .boardUrl("board")
                .creator(user)
                .build();

        post = Post.builder()
                .title("title")
                .contents("content")
                .user(user)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", 10L);
    }

    @Test
    @DisplayName("insert 후 조회 이력이 없으면 POST_NOT_FOUND 예외로 변환한다")
    void getOrCreate_insertedButMissing_throwsPostNotFound() {
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId())).thenReturn(1);

        assertThatThrownBy(() -> viewHistoryCommandService.getOrCreate(user, post))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("touch 갱신 대상이 없으면 POST_NOT_FOUND 예외로 변환한다")
    void touchView_missingAfterInsertConflict_throwsPostNotFound() {
        when(viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId())).thenReturn(0);
        when(viewHistoryRepository.touchModifiedAt(user.getUserId(), post.getPostId())).thenReturn(0);

        assertThatThrownBy(() -> viewHistoryCommandService.touchView(user, post))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("touch-and-load는 갱신된 조회 이력을 반환한다")
    void touchAndLoadView_existingHistory_returnsLoadedHistory() {
        ViewHistory viewHistory = ViewHistory.builder().user(user).post(post).build();
        when(viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId())).thenReturn(0);
        when(viewHistoryRepository.touchModifiedAt(user.getUserId(), post.getPostId())).thenReturn(1);
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.of(viewHistory));

        ViewHistory result = viewHistoryCommandService.touchAndLoadView(user, post);

        assertThat(result).isSameAs(viewHistory);
    }

    @Test
    @DisplayName("insert 후 lock 조회 이력이 없으면 POST_NOT_FOUND 예외로 변환한다")
    void getOrCreateForUpdate_insertedButMissing_throwsPostNotFound() {
        when(viewHistoryRepository.findByUserAndPostForUpdate(user.getUserId(), post.getPostId()))
                .thenReturn(Optional.empty());
        when(viewHistoryRepository.insertIgnore(user.getUserId(), post.getPostId())).thenReturn(1);

        assertThatThrownBy(() -> viewHistoryCommandService.getOrCreateForUpdate(user, post))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }
}
