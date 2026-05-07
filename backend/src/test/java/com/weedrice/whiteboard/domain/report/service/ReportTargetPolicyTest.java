package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTargetPolicyTest {

    @Mock
    private UserBlockService userBlockService;
    @Mock
    private PostAccessPolicy postAccessPolicy;

    @InjectMocks
    private ReportTargetPolicy reportTargetPolicy;

    @Test
    @DisplayName("POST report policy uses either-direction block state")
    void validatePostReportable_usesEitherDirectionBlockCheck() {
        User reporter = user(1L);
        User author = user(2L);
        Post post = post(author);
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(true);

        reportTargetPolicy.validatePostReportable(post, reporter);

        verify(postAccessPolicy).validateReadable(post, reporter, true);
    }

    @Test
    @DisplayName("POST report preserves visibility failure when either direction is blocked")
    void validatePostReportable_preservesVisibilityFailureWhenBlockedEitherDirection() {
        User reporter = user(1L);
        User author = user(2L);
        Post post = post(author);
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(true);
        doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(postAccessPolicy).validateReadable(post, reporter, true);

        assertThatThrownBy(() -> reportTargetPolicy.validatePostReportable(post, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("POST report rejects self-report as INVALID_TARGET")
    void validatePostReportable_rejectsSelfReport() {
        User reporter = user(1L);
        Post post = post(reporter);

        assertThatThrownBy(() -> reportTargetPolicy.validatePostReportable(post, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TARGET);

        verify(postAccessPolicy, never()).validateReadable(post, reporter, false);
        verify(userBlockService, never()).isEitherDirectionBlocked(1L, 1L);
    }

    @Test
    @DisplayName("COMMENT report policy checks post author and comment author block state")
    void validateCommentReportable_checksCommentAuthorAndPostAuthor() {
        User reporter = user(1L);
        User postAuthor = user(2L);
        User commentAuthor = user(3L);
        Post post = post(postAuthor);
        Comment comment = comment(post, commentAuthor);
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        when(userBlockService.isEitherDirectionBlocked(1L, 3L)).thenReturn(false);

        reportTargetPolicy.validateCommentReportable(comment, reporter);

        verify(postAccessPolicy).validateReadable(post, reporter, false);
    }

    @Test
    @DisplayName("COMMENT report rejects blocked comment author as COMMENT_NOT_FOUND")
    void validateCommentReportable_rejectsBlockedCommentAuthor() {
        User reporter = user(1L);
        User postAuthor = user(2L);
        User commentAuthor = user(3L);
        Comment comment = comment(post(postAuthor), commentAuthor);
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        when(userBlockService.isEitherDirectionBlocked(1L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> reportTargetPolicy.validateCommentReportable(comment, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(postAccessPolicy).validateReadable(comment.getPost(), reporter, false);
    }

    @Test
    @DisplayName("COMMENT report rejects self-report as INVALID_TARGET")
    void validateCommentReportable_rejectsSelfReport() {
        User reporter = user(1L);
        User postAuthor = user(2L);
        Comment comment = comment(post(postAuthor), reporter);
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> reportTargetPolicy.validateCommentReportable(comment, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TARGET);

        verify(postAccessPolicy).validateReadable(comment.getPost(), reporter, false);
        verify(userBlockService, never()).isEitherDirectionBlocked(1L, 1L);
    }

    @Test
    @DisplayName("COMMENT report preserves parent post visibility failure before comment author block check")
    void validateCommentReportable_preservesParentPostVisibilityFailure() {
        User reporter = user(1L);
        User postAuthor = user(2L);
        User commentAuthor = user(3L);
        Comment comment = comment(post(postAuthor), commentAuthor);
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(postAccessPolicy).validateReadable(comment.getPost(), reporter, false);

        assertThatThrownBy(() -> reportTargetPolicy.validateCommentReportable(comment, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(userBlockService, never()).isEitherDirectionBlocked(1L, 3L);
    }

    @Test
    @DisplayName("USER report rejects self-report as INVALID_TARGET")
    void validateUserReportable_rejectsSelfReport() {
        User reporter = user(1L);
        User target = user(1L);

        assertThatThrownBy(() -> reportTargetPolicy.validateUserReportable(target, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TARGET);

        verify(userBlockService, never()).isEitherDirectionBlocked(1L, 1L);
    }

    @Test
    @DisplayName("USER report hides either-direction blocked target as USER_NOT_FOUND")
    void validateUserReportable_rejectsEitherDirectionBlockedTarget() {
        User reporter = user(1L);
        User target = user(2L);
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> reportTargetPolicy.validateUserReportable(target, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    private Comment comment(Post post, User author) {
        return Comment.builder()
                .post(post)
                .user(author)
                .depth(0)
                .content("comment")
                .build();
    }

    private Post post(User author) {
        Board board = Board.builder()
                .boardName("board")
                .boardUrl("board")
                .creator(author)
                .isPublic(true)
                .build();
        Post post = Post.builder()
                .board(board)
                .user(author)
                .title("title")
                .contents("contents")
                .build();
        ReflectionTestUtils.setField(post, "postId", 10L);
        return post;
    }

    private User user(Long userId) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
