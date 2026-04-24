package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTargetValidatorTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private PostAccessPolicy postAccessPolicy;

    @InjectMocks
    private ReportTargetValidator reportTargetValidator;

    @Test
    @DisplayName("POST target validation uses post access policy without DTO assembly")
    void validatePost_usesPostAccessPolicy() {
        User reporter = user(1L);
        Post post = post(user(2L));

        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of(2L));

        reportTargetValidator.validate("POST", 10L, reporter);

        verify(postAccessPolicy).validateReadable(post, reporter, true);
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
    @DisplayName("COMMENT target validation checks parent post readability")
    void validateComment_usesParentPostAccessPolicy() {
        User reporter = user(1L);
        Post post = post(user(2L));
        Comment comment = Comment.builder()
                .post(post)
                .user(user(3L))
                .depth(0)
                .content("comment")
                .build();

        when(commentRepository.findNonDeletedByIdWithRelations(20L)).thenReturn(Optional.of(comment));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of());

        reportTargetValidator.validate("COMMENT", 20L, reporter);

        verify(postAccessPolicy).validateReadable(post, reporter, false);
    }

    @Test
    @DisplayName("missing COMMENT target maps to COMMENT_NOT_FOUND")
    void validateComment_missing_throwsCommentNotFound() {
        User reporter = user(1L);
        when(commentRepository.findNonDeletedByIdWithRelations(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportTargetValidator.validate("COMMENT", 20L, reporter))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("USER target validation keeps user existence check")
    void validateUser_usesUserRepository() {
        User target = user(30L);
        when(userRepository.findById(30L)).thenReturn(Optional.of(target));

        reportTargetValidator.validate("USER", 30L, user(1L));

        verify(userRepository).findById(30L);
    }

    @Test
    @DisplayName("missing USER target maps to USER_NOT_FOUND")
    void validateUser_missing_throwsUserNotFound() {
        when(userRepository.findById(30L)).thenReturn(Optional.empty());

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
