package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesRepository;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledPostRequestPolicyTest {

    @Mock BoardCategoryRepository boardCategoryRepository;
    @Mock BoardAccessPolicy boardAccessPolicy;
    @Mock PostAuthorCommandPolicy postAuthorCommandPolicy;
    @Mock PostSeriesRepository postSeriesRepository;

    private ScheduledPostRequestPolicy policy;
    private User user;
    private Board board;

    @BeforeEach
    void setUp() {
        policy = new ScheduledPostRequestPolicy(
                boardCategoryRepository,
                boardAccessPolicy,
                postAuthorCommandPolicy,
                postSeriesRepository);
        user = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        board = Board.builder().boardName("Board").boardUrl("board").build();
        ReflectionTestUtils.setField(board, "boardId", 2L);
    }

    @Test
    void rejectsCategoryThatIsNotActiveOnTargetBoard() {
        ScheduledPostRequest request = request(10L, false, null);
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(10L, 2L, true))
                .thenReturn(Optional.empty());

        assertError(() -> policy.validate(user, board, request), ErrorCode.NOT_FOUND);

        verify(postAuthorCommandPolicy, never()).validateAppliedCategoryWriteRole(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWhenUserDoesNotMeetCategoryWriteRole() {
        ScheduledPostRequest request = request(10L, false, null);
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("Managers")
                .minWriteRole(Role.BOARD_ADMIN)
                .build();
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(10L, 2L, true))
                .thenReturn(Optional.of(category));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(postAuthorCommandPolicy).validateAppliedCategoryWriteRole(board, user, category);

        assertError(() -> policy.validate(user, board, request), ErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsNoticeWithoutBoardAdminPermission() {
        ScheduledPostRequest request = request(null, true, null);
        when(boardAccessPolicy.hasBoardAdminAccess(board, user)).thenReturn(false);

        assertError(() -> policy.validate(user, board, request), ErrorCode.FORBIDDEN);

        verify(postSeriesRepository, never()).findBySeriesIdAndOwner_UserId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsSeriesNotOwnedByAuthor() {
        ScheduledPostRequest request = request(null, false, 30L);
        when(postSeriesRepository.findBySeriesIdAndOwner_UserId(30L, 1L)).thenReturn(Optional.empty());

        assertError(() -> policy.validate(user, board, request), ErrorCode.NOT_FOUND);
    }

    @Test
    void acceptsActiveCategoryPermissionNoticePermissionAndOwnedSeries() {
        ScheduledPostRequest request = request(10L, true, 30L);
        BoardCategory category = BoardCategory.builder().board(board).name("General").build();
        PostSeries series = PostSeries.builder().owner(user).title("Series").build();
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(10L, 2L, true))
                .thenReturn(Optional.of(category));
        when(boardAccessPolicy.hasBoardAdminAccess(board, user)).thenReturn(true);
        when(postSeriesRepository.findBySeriesIdAndOwner_UserId(30L, 1L)).thenReturn(Optional.of(series));

        policy.validate(user, board, request);

        verify(postAuthorCommandPolicy).validateAppliedCategoryWriteRole(board, user, category);
        verify(boardAccessPolicy).hasBoardAdminAccess(board, user);
        verify(postSeriesRepository).findBySeriesIdAndOwner_UserId(30L, 1L);
    }

    private ScheduledPostRequest request(Long categoryId, boolean notice, Long seriesId) {
        ScheduledPostRequest request = new ScheduledPostRequest();
        ReflectionTestUtils.setField(request, "categoryId", categoryId);
        ReflectionTestUtils.setField(request, "isNotice", notice);
        ReflectionTestUtils.setField(request, "seriesId", seriesId);
        return request;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ErrorCode errorCode) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
