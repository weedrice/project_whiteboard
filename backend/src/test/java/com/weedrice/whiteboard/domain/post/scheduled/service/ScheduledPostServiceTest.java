package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledPostServiceTest {

    @Mock ScheduledPostRepository scheduledPostRepository;
    @Mock DraftPostRepository draftPostRepository;
    @Mock BoardRepository boardRepository;
    @Mock UserWritableResolver userWritableResolver;
    @Mock SanctionService sanctionService;
    @Mock PostAuthorCommandPolicy postAuthorCommandPolicy;
    @Mock ScheduledPostPayloadMapper payloadMapper;
    @Mock ScheduledPostPublishWorker publishWorker;

    private ScheduledPostService service;
    private User user;
    private Board board;
    private DraftPost draft;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);
        service = new ScheduledPostService(
                scheduledPostRepository,
                draftPostRepository,
                boardRepository,
                userWritableResolver,
                sanctionService,
                postAuthorCommandPolicy,
                payloadMapper,
                publishWorker,
                clock);
        user = User.builder().email("scheduled@example.com").displayName("scheduled-user").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        board = Board.builder().boardName("Scheduled Board").boardUrl("scheduled-board").build();
        ReflectionTestUtils.setField(board, "boardId", 2L);
        draft = DraftPost.builder().user(user).board(board).title("draft").build();
        ReflectionTestUtils.setField(draft, "draftId", 77L);
    }

    @Test
    void createValidatesAndStoresOwnedDraftOnSameBoard() {
        ScheduledPostRequest request = requestWithDraft(77L);
        when(userWritableResolver.resolve(1L)).thenReturn(user);
        when(boardRepository.findByBoardUrl("scheduled-board")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(77L, user)).thenReturn(Optional.of(draft));
        when(scheduledPostRepository.saveAndFlush(any(ScheduledPost.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(1L, "scheduled-board", request);

        assertThat(response.getDraftId()).isEqualTo(77L);
        verify(draftPostRepository).findByDraftIdAndUserForUpdate(77L, user);
        verify(scheduledPostRepository).existsByDraftId(77L);
    }

    @Test
    void createRejectsDraftAlreadyReferencedByAnotherSchedule() {
        ScheduledPostRequest request = requestWithDraft(77L);
        when(userWritableResolver.resolve(1L)).thenReturn(user);
        when(boardRepository.findByBoardUrl("scheduled-board")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(77L, user)).thenReturn(Optional.of(draft));
        when(scheduledPostRepository.existsByDraftId(77L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, "scheduled-board", request))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class);

        verify(scheduledPostRepository, never()).saveAndFlush(any(ScheduledPost.class));
    }

    @Test
    void updateValidatesAndStoresOwnedDraftOnSameBoard() {
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(user).board(board).title("old").contents("old")
                .scheduledAt(LocalDateTime.of(2026, 7, 13, 1, 0)).build();
        ScheduledPostRequest request = requestWithDraft(77L);
        when(userWritableResolver.resolve(1L)).thenReturn(user);
        when(scheduledPostRepository.findOwnedForUpdate(9L, 1L))
                .thenReturn(Optional.of(scheduledPost));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(77L, user)).thenReturn(Optional.of(draft));

        var response = service.update(1L, 9L, request);

        assertThat(response.getDraftId()).isEqualTo(77L);
        assertThat(scheduledPost.getDraftId()).isEqualTo(77L);
        verify(scheduledPostRepository).existsByDraftIdAndScheduledPostIdNot(77L, 9L);
        verify(scheduledPostRepository).findOwnedForUpdate(9L, 1L);
    }

    @Test
    void publishDuePostsRecoversExpiredPublishingLeaseBeforeSelectingDuePosts() {
        when(scheduledPostRepository.recoverStalePublishing(LocalDateTime.of(2026, 7, 12, 23, 50)))
                .thenReturn(1);
        when(scheduledPostRepository.findDueScheduledPostIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.publishDuePosts()).isZero();

        verify(scheduledPostRepository)
                .recoverStalePublishing(LocalDateTime.of(2026, 7, 12, 23, 50));
    }

    @Test
    void publishDuePostsContinuesBatchAndMarksFailedOutsidePublishAttempt() {
        ScheduledPostRepository.ScheduledPostIdProjection first = () -> 1L;
        ScheduledPostRepository.ScheduledPostIdProjection second = () -> 2L;
        when(scheduledPostRepository.findDueScheduledPostIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(publishWorker.claim(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(true);
        when(publishWorker.claim(eq(2L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(true);
        doThrow(new IllegalStateException("publish failed"))
                .when(publishWorker).publishClaimed(eq(1L), any(LocalDateTime.class));

        assertThat(service.publishDuePosts()).isEqualTo(1);

        verify(publishWorker).markFailed(eq(1L), any(LocalDateTime.class), any(IllegalStateException.class));
        verify(publishWorker).publishClaimed(eq(2L), any(LocalDateTime.class));
    }

    @Test
    void publishDuePostsRunsWithoutAnOuterTransaction() throws Exception {
        Method method = ScheduledPostService.class.getMethod("publishDuePosts");
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void updateLookupUsesPessimisticWriteLock() throws Exception {
        Method method = ScheduledPostRepository.class
                .getMethod("findOwnedForUpdate", Long.class, Long.class);

        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private ScheduledPostRequest requestWithDraft(Long draftId) {
        ScheduledPostRequest request = new ScheduledPostRequest();
        ReflectionTestUtils.setField(request, "title", "Scheduled title");
        ReflectionTestUtils.setField(request, "contents", "Scheduled contents");
        ReflectionTestUtils.setField(request, "draftId", draftId);
        ReflectionTestUtils.setField(request, "scheduledAt", LocalDateTime.of(2026, 7, 13, 0, 10));
        return request;
    }
}
