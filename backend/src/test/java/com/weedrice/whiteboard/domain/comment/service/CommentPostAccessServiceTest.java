package com.weedrice.whiteboard.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommentPostAccessServiceTest {

    @Test
    void validateReadable_rejectsCommentsOfBlindedPost() {
        User author = User.builder().loginId("author").displayName("Author").build();
        Board board = Board.builder().boardName("Free").boardUrl("free").build();
        Post post = Post.builder()
                .board(board)
                .user(author)
                .title("Blinded")
                .contents("hidden")
                .build();
        post.blind("reported", LocalDateTime.now());
        CommentPostAccessService service = new CommentPostAccessService(
                mock(UserBlockService.class),
                new PostAccessPolicy(new BoardAccessPolicy(mock(AdminRepository.class))));

        assertThatThrownBy(() -> service.validateReadable(post, new CommentReadContext(null, Set.of())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
