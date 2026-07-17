package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentTopicAccessServiceTest {

    @Mock PostRepository postRepository;
    @Mock BoardRepository boardRepository;
    @Mock UserRepository userRepository;
    @Mock UserBlockService userBlockService;
    @Mock PostAccessPolicy postAccessPolicy;
    @Mock NotificationSseEmitterRegistry emitterRegistry;
    @InjectMocks CommentTopicAccessService service;

    @Test
    void rejectsBlindedPostBeforeBlockAndAccessChecks() {
        User viewer = mock(User.class);
        Post post = mock(Post.class);
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(1L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(viewer));
        when(postRepository.findByIdWithRelations(2L)).thenReturn(Optional.of(post));
        when(post.getIsBlinded()).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.validateReadable(1L, 2L));

        assertSame(ErrorCode.POST_NOT_FOUND, exception.getErrorCode());
        verify(userBlockService, never()).isEitherDirectionBlocked(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(postAccessPolicy, never()).validateReadable(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void subscribesWhileHoldingBoardWriteLock() {
        User viewer = mock(User.class);
        User author = mock(User.class);
        Post post = mock(Post.class);
        Board board = mock(Board.class);
        when(postRepository.findBoardIdByPostId(2L)).thenReturn(Optional.of(3L));
        when(boardRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(board));
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(1L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(viewer));
        when(postRepository.findByIdWithRelations(2L)).thenReturn(Optional.of(post));
        when(post.getBoard()).thenReturn(board);
        when(board.getBoardId()).thenReturn(3L);
        when(post.getUser()).thenReturn(author);
        when(viewer.getUserId()).thenReturn(1L);
        when(author.getUserId()).thenReturn(4L);

        service.subscribeReadable(1L, 2L, "connection-1");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(boardRepository, emitterRegistry);
        order.verify(boardRepository).findByIdForUpdate(3L);
        order.verify(emitterRegistry).subscribeCommentTopic(1L, 3L, 2L, "connection-1");
    }
}
