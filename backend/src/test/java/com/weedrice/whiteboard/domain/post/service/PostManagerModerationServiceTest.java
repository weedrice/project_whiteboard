package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostManagerModerationServiceTest {

    @Mock PostRepository posts;
    @Mock BoardRepository boards;
    @Mock UserRepository users;
    @Mock BoardAccessPolicy accessPolicy;
    @Mock ModerationAuditLogService audits;
    @Mock SemanticSearchEventPublisher semanticSearchEvents;
    @Mock NotificationAccessInvalidationService notificationAccessInvalidationService;
    @Mock AnonymousReadCacheInvalidator anonymousReadCacheInvalidator;
    PostManagerModerationService service;
    User manager;
    Post post;
    Board board;

    @BeforeEach
    void setUp() {
        service = new PostManagerModerationService(posts, boards, users, accessPolicy, audits, semanticSearchEvents,
                notificationAccessInvalidationService,
                anonymousReadCacheInvalidator,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        manager = mock(User.class);
        post = mock(Post.class);
        board = mock(Board.class);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(manager));
        when(posts.findBoardIdByPostId(2L)).thenReturn(Optional.of(10L));
        when(boards.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(posts.findByIdWithRelationsForBlindUpdate(2L)).thenReturn(Optional.of(post));
        when(post.getBoard()).thenReturn(board);
        when(board.getBoardId()).thenReturn(10L);
        when(post.getPostId()).thenReturn(2L);
        when(post.getIsDeleted()).thenReturn(false);
    }

    @Test
    void locksBoardBeforePostAndRevalidatesManagerInsideBoardLock() {
        service.blindPost(1L, 2L, null);

        var ordered = inOrder(posts, boards, accessPolicy);
        ordered.verify(posts).findBoardIdByPostId(2L);
        ordered.verify(boards).findByIdForUpdate(10L);
        ordered.verify(posts).findByIdWithRelationsForBlindUpdate(2L);
        ordered.verify(accessPolicy).validateBoardAdmin(board, manager);
    }

    @Test
    void blindNormalizesReasonAndUnblindAudits() {
        service.blindPost(1L, 2L, "  ");
        service.unblindPost(1L, 2L);

        verify(semanticSearchEvents).publish("POST", 2L, SemanticSearchIndexAction.DELETE);
        verify(semanticSearchEvents).publish("POST", 2L, SemanticSearchIndexAction.UPSERT);
        verify(semanticSearchEvents).publishPostCommentsReindex(2L);
        verify(anonymousReadCacheInvalidator, org.mockito.Mockito.times(2))
                .evictPostRelatedCachesAfterCommit();

        verify(post).blind("MANAGER", LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(notificationAccessInvalidationService).invalidateCommentTopicAfterCommit(2L);
        verify(post).unblind();
        verify(audits).recordUserAction(manager, ModerationAuditLogService.ACTION_POST_BLIND,
                ModerationAuditLogService.TARGET_TYPE_POST, 2L, board, "MANAGER");
    }

    @Test
    void missingManagerPostOrDeletedPostIsRejected() {
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.blindPost(9L, 2L, null));

        when(posts.findByIdWithRelationsForBlindUpdate(8L)).thenReturn(Optional.empty());
        when(posts.findBoardIdByPostId(8L)).thenReturn(Optional.of(10L));
        assertThrows(BusinessException.class, () -> service.blindPost(1L, 8L, null));

        when(post.getIsDeleted()).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.blindPost(1L, 2L, null));
    }
}
