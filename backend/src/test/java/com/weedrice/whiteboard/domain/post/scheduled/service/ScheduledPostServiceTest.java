package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
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
    @Mock ScheduledPostRequestPolicy scheduledPostRequestPolicy;
    @Mock ScheduledPostPayloadMapper payloadMapper;
    @Mock ScheduledPostPublishWorker publishWorker;
    @Mock ScheduledPostFileService scheduledPostFileService;

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
                scheduledPostRequestPolicy,
                payloadMapper,
                publishWorker,
                scheduledPostFileService,
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
        ReflectionTestUtils.setField(request, "fileIds", List.of(10L, 11L));
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(boardRepository.findByBoardUrl("scheduled-board")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(77L, user)).thenReturn(Optional.of(draft));
        when(scheduledPostRepository.saveAndFlush(any(ScheduledPost.class)))
                .thenAnswer(invocation -> {
                    ScheduledPost saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "scheduledPostId", 9L);
                    return saved;
                });

        var response = service.create(1L, "scheduled-board", request);

        assertThat(response.getDraftId()).isEqualTo(77L);
        var lockOrder = org.mockito.Mockito.inOrder(userWritableResolver, boardRepository);
        lockOrder.verify(userWritableResolver).resolveForUpdate(1L);
        lockOrder.verify(boardRepository).findByBoardUrl("scheduled-board");
        verify(draftPostRepository).findByDraftIdAndUserForUpdate(77L, user);
        verify(scheduledPostRepository).existsByDraftId(77L);
        verify(scheduledPostFileService).replaceReferences(9L, 1L, 77L, List.of(10L, 11L));
    }

    @Test
    void createRejectsDraftAlreadyReferencedByAnotherSchedule() {
        ScheduledPostRequest request = requestWithDraft(77L);
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(boardRepository.findByBoardUrl("scheduled-board")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(77L, user)).thenReturn(Optional.of(draft));
        when(scheduledPostRepository.existsByDraftId(77L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, "scheduled-board", request))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class);

        verify(scheduledPostRepository, never()).saveAndFlush(any(ScheduledPost.class));
    }

    @Test
    void createRejectsPollThatClosesAtOrBeforeScheduledPublication() {
        ScheduledPostRequest request = requestWithDraft(null);
        setPollClosesAt(request, request.getScheduledAt());
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(boardRepository.findByBoardUrl("scheduled-board")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> service.create(1L, "scheduled-board", request))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.weedrice.whiteboard.global.exception.ErrorCode.INVALID_INPUT_VALUE);

        verify(scheduledPostRepository, never()).saveAndFlush(any(ScheduledPost.class));
    }

    @Test
    void createRejectsPollThatCanExpireBeforeTheNextPublisherTick() {
        ScheduledPostRequest request = requestWithDraft(null);
        setPollClosesAt(request, request.getScheduledAt().plusSeconds(30));
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(boardRepository.findByBoardUrl("scheduled-board")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> service.create(1L, "scheduled-board", request))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.weedrice.whiteboard.global.exception.ErrorCode.INVALID_INPUT_VALUE);

        verify(scheduledPostRepository, never()).saveAndFlush(any(ScheduledPost.class));
    }

    @Test
    void createAllowsPollThatClosesExactlyOneMinuteAfterScheduledPublication() {
        ScheduledPostRequest request = requestWithDraft(null);
        setPollClosesAt(request, request.getScheduledAt().plusMinutes(1));
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(boardRepository.findByBoardUrl("scheduled-board")).thenReturn(Optional.of(board));
        when(scheduledPostRepository.saveAndFlush(any(ScheduledPost.class)))
                .thenAnswer(invocation -> {
                    ScheduledPost saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "scheduledPostId", 9L);
                    return saved;
                });

        service.create(1L, "scheduled-board", request);

        verify(scheduledPostRepository).saveAndFlush(any(ScheduledPost.class));
    }

    @Test
    void createRejectsPublicationPolicyViolationBeforePersisting() {
        ScheduledPostRequest request = requestWithDraft(null);
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(boardRepository.findByBoardUrl("scheduled-board")).thenReturn(Optional.of(board));
        doThrow(new com.weedrice.whiteboard.global.exception.BusinessException(
                com.weedrice.whiteboard.global.exception.ErrorCode.FORBIDDEN))
                .when(scheduledPostRequestPolicy).validate(user, board, request);

        assertThatThrownBy(() -> service.create(1L, "scheduled-board", request))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.weedrice.whiteboard.global.exception.ErrorCode.FORBIDDEN);

        verify(scheduledPostRepository, never()).saveAndFlush(any(ScheduledPost.class));
        verify(scheduledPostFileService, never()).replaceReferences(any(), any(), any(), any());
    }

    @Test
    void updateValidatesAndStoresOwnedDraftOnSameBoard() {
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(user).board(board).title("old").contents("old")
                .scheduledAt(LocalDateTime.of(2026, 7, 13, 1, 0)).build();
        ScheduledPostRequest request = requestWithDraft(77L);
        ReflectionTestUtils.setField(request, "fileIds", List.of(10L));
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(scheduledPostRepository.findOwnedForUpdate(9L, 1L))
                .thenReturn(Optional.of(scheduledPost));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(77L, user)).thenReturn(Optional.of(draft));

        var response = service.update(1L, 9L, request);

        assertThat(response.getDraftId()).isEqualTo(77L);
        assertThat(scheduledPost.getDraftId()).isEqualTo(77L);
        var lockOrder = org.mockito.Mockito.inOrder(userWritableResolver, scheduledPostRepository);
        lockOrder.verify(userWritableResolver).resolveForUpdate(1L);
        lockOrder.verify(scheduledPostRepository).findOwnedForUpdate(9L, 1L);
        verify(scheduledPostRepository).existsByDraftIdAndScheduledPostIdNot(77L, 9L);
        verify(scheduledPostRepository).findOwnedForUpdate(9L, 1L);
        verify(scheduledPostFileService).replaceReferences(9L, 1L, 77L, List.of(10L));
    }

    @Test
    void updateRejectsPollThatClosesBeforeScheduledPublicationWithoutMutation() {
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(user).board(board).title("old").contents("old")
                .scheduledAt(LocalDateTime.of(2026, 7, 13, 1, 0)).build();
        ScheduledPostRequest request = requestWithDraft(null);
        setPollClosesAt(request, request.getScheduledAt().minusSeconds(1));
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(scheduledPostRepository.findOwnedForUpdate(9L, 1L)).thenReturn(Optional.of(scheduledPost));

        assertThatThrownBy(() -> service.update(1L, 9L, request))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.weedrice.whiteboard.global.exception.ErrorCode.INVALID_INPUT_VALUE);

        assertThat(scheduledPost.getTitle()).isEqualTo("old");
        assertThat(scheduledPost.getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 7, 13, 1, 0));
        verify(scheduledPostRepository, never()).flush();
        verify(scheduledPostFileService, never()).replaceReferences(any(), any(), any(), any());
    }

    @Test
    void updateRejectsPublicationPolicyViolationWithoutMutatingState() {
        LocalDateTime originalScheduledAt = LocalDateTime.of(2026, 7, 13, 1, 0);
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(user).board(board).title("old").contents("old")
                .scheduledAt(originalScheduledAt).build();
        ScheduledPostRequest request = requestWithDraft(null);
        when(userWritableResolver.resolveForUpdate(1L)).thenReturn(user);
        when(scheduledPostRepository.findOwnedForUpdate(9L, 1L)).thenReturn(Optional.of(scheduledPost));
        doThrow(new com.weedrice.whiteboard.global.exception.BusinessException(
                com.weedrice.whiteboard.global.exception.ErrorCode.FORBIDDEN))
                .when(scheduledPostRequestPolicy).validate(user, board, request);

        assertThatThrownBy(() -> service.update(1L, 9L, request))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.weedrice.whiteboard.global.exception.ErrorCode.FORBIDDEN);

        assertThat(scheduledPost.getTitle()).isEqualTo("old");
        assertThat(scheduledPost.getContents()).isEqualTo("old");
        assertThat(scheduledPost.getScheduledAt()).isEqualTo(originalScheduledAt);
        verify(scheduledPostRepository, never()).flush();
        verify(scheduledPostFileService, never()).replaceReferences(any(), any(), any(), any());
    }

    @Test
    void cancelRemovesScheduledFileReferencesAfterStateTransition() {
        when(userWritableResolver.resolve(1L)).thenReturn(user);
        when(scheduledPostRepository.cancelOwned(eq(9L), eq(1L), any(LocalDateTime.class))).thenReturn(1);

        service.cancel(1L, 9L);

        verify(scheduledPostFileService).removeReferences(9L);
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

    private void setPollClosesAt(ScheduledPostRequest request, LocalDateTime closesAt) {
        PollRequest poll = new PollRequest();
        poll.setQuestion("Question");
        poll.setOptions(List.of("A", "B"));
        poll.setClosesAt(closesAt);
        ReflectionTestUtils.setField(request, "poll", poll);
    }
}
