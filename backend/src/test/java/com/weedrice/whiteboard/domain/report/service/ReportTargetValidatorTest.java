package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSupport;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTargetValidatorTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportTargetPolicy reportTargetPolicy;
    @Mock
    private CommentReadSupport commentReadSupport;

    @InjectMocks
    private ReportTargetValidator reportTargetValidator;

    @Test
    @DisplayName("POST target validation loads target and delegates to policy")
    void validatePost_delegatesToPolicy() {
        User reporter = user(1L);
        Post post = post(user(2L));

        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.of(post));

        reportTargetValidator.validate("POST", 10L, reporter);

        verify(reportTargetPolicy).validatePostReportable(post, reporter);
    }

    @Test
    @DisplayName("missing POST target maps to POST_NOT_FOUND")
    void validatePost_missing_throwsPostNotFound() {
        User reporter = user(1L);
        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportTargetValidator.validate("POST", 10L, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("COMMENT target validation loads target and delegates to policy")
    void validateComment_delegatesToPolicy() {
        User reporter = user(1L);
        Post post = post(user(2L));
        Comment comment = Comment.builder()
                .post(post)
                .user(user(3L))
                .depth(0)
                .content("comment")
                .build();

        when(commentReadSupport.getNonDeletedWithRelationsOrThrow(20L)).thenReturn(comment);

        reportTargetValidator.validate("COMMENT", 20L, reporter);

        verify(reportTargetPolicy).validateCommentReportable(comment, reporter);
    }

    @Test
    @DisplayName("missing COMMENT target maps to COMMENT_NOT_FOUND")
    void validateComment_missing_throwsCommentNotFound() {
        User reporter = user(1L);
        when(commentReadSupport.getNonDeletedWithRelationsOrThrow(20L))
                .thenThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        assertThatThrownBy(() -> reportTargetValidator.validate("COMMENT", 20L, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("USER target validation loads active user and delegates to policy")
    void validateUser_usesUserRepository() {
        User reporter = user(1L);
        User target = user(30L);
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(30L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(target));

        reportTargetValidator.validate("USER", 30L, reporter);

        verify(reportTargetPolicy).validateUserReportable(target, reporter);
    }

    @Test
    @DisplayName("missing or inactive USER target maps to USER_NOT_FOUND")
    void validateUser_missingOrInactive_throwsUserNotFound() {
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(30L, User.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportTargetValidator.validate("USER", 30L, user(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
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
