package com.weedrice.whiteboard.domain.moderation.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.moderation.dto.ModerationAuditLogSearchCondition;
import com.weedrice.whiteboard.domain.moderation.entity.ModerationAuditLog;
import com.weedrice.whiteboard.domain.moderation.repository.ModerationAuditLogRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationAuditLogServiceTest {

    @Mock ModerationAuditLogRepository audits;
    @Mock BoardRepository boards;
    @Mock UserReadableResolver users;
    @Mock BoardAccessPolicy accessPolicy;
    ModerationAuditLogService service;

    @BeforeEach
    void setUp() {
        service = new ModerationAuditLogService(audits, boards, users, accessPolicy);
    }

    @Test
    void recordsUserAndSystemActionsWithNormalizedReason() {
        User user = mock(User.class);
        Board board = mock(Board.class);

        service.recordUserAction(user, "ACTION", "POST", 1L, board, " reason ");
        service.recordSystemAction("SYSTEM", "POST", 2L, board, " ");

        ArgumentCaptor<ModerationAuditLog> captor = ArgumentCaptor.forClass(ModerationAuditLog.class);
        verify(audits, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("reason", captor.getAllValues().get(0).getReason());
        assertNull(captor.getAllValues().get(1).getReason());
    }

    @Test
    void adminSearchNormalizesFiltersAndDateBounds() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(audits.search(any(), org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(Page.empty(pageable));

        service.getAdminAudits(" PIN ", " USER ", 3L, " board ", 4L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), pageable);

        ArgumentCaptor<ModerationAuditLogSearchCondition> captor =
                ArgumentCaptor.forClass(ModerationAuditLogSearchCondition.class);
        verify(audits).search(captor.capture(), org.mockito.ArgumentMatchers.eq(pageable));
        assertEquals("PIN", captor.getValue().action());
        assertEquals("board", captor.getValue().boardUrl());
        assertEquals(LocalDateTime.of(2026, 1, 3, 0, 0), captor.getValue().createdTo());
    }

    @Test
    void managerSearchChecksBoardScopeAndMissingBoard() {
        User manager = mock(User.class);
        Board board = mock(Board.class);
        when(board.getBoardId()).thenReturn(7L);
        when(users.resolve(1L)).thenReturn(manager);
        when(boards.findByBoardUrl("general")).thenReturn(Optional.of(board));
        PageRequest pageable = PageRequest.of(0, 10);
        when(audits.search(any(), org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(Page.empty(pageable));

        service.getBoardManagerAudits(1L, "general", null, null, null, null, null, pageable);
        verify(accessPolicy).validateBoardAdmin(board, manager);

        when(boards.findByBoardUrl("missing")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.getBoardManagerAudits(
                1L, "missing", null, null, null, null, null, pageable));
    }

    @Test
    void rejectsEmptyOrReversedDateRange() {
        assertThrows(BusinessException.class, () -> service.getAdminAudits(
                null, null, null, null, null,
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), PageRequest.of(0, 10)));
    }
}
