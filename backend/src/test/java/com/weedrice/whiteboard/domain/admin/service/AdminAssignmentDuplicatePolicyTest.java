package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAssignmentDuplicatePolicyTest {

    @Mock
    private AdminRepository adminRepository;

    private AdminAssignmentDuplicatePolicy duplicatePolicy;
    private User user;
    private Board board;
    private Admin admin;

    @BeforeEach
    void setUp() {
        duplicatePolicy = new AdminAssignmentDuplicatePolicy(adminRepository);
        user = User.builder()
                .loginId("testUser")
                .password("encoded")
                .email("test@example.com")
                .displayName("test")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        board = Board.builder()
                .boardName("test")
                .boardUrl("test")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        admin = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(admin, "adminId", 100L);
    }

    @Test
    void validateNoActiveAdmin_throwsDuplicateWhenActiveRowExists() {
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> duplicatePolicy.validateNoActiveAdmin(user, board, Role.BOARD_ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void validateNoOtherActiveAdmin_allowsSameAdmin() {
        when(adminRepository.countByBoardAndRoleAndIsActiveAndAdminIdNot(
                board, Role.BOARD_ADMIN, true, 100L))
                .thenReturn(0L);

        duplicatePolicy.validateNoOtherActiveAdmin(admin, board);
    }

    @Test
    void validateNoOtherActiveAdmin_throwsDuplicateWhenAnotherActiveRowExists() {
        when(adminRepository.countByBoardAndRoleAndIsActiveAndAdminIdNot(
                board, Role.BOARD_ADMIN, true, 100L))
                .thenReturn(1L);

        assertThatThrownBy(() -> duplicatePolicy.validateNoOtherActiveAdmin(admin, board))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void validateNoOtherActiveAdmin_allowsModeratorWhenOnlyOtherUsersAreActive() {
        String moderatorRole = "MODERATOR";
        Admin moderator = Admin.builder().user(user).board(board).role(moderatorRole).build();
        ReflectionTestUtils.setField(moderator, "adminId", 101L);
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, moderatorRole, true))
                .thenReturn(Optional.empty());

        duplicatePolicy.validateNoOtherActiveAdmin(moderator, board);
    }

    @Test
    void validateNoOtherActiveAdmin_throwsDuplicateForSameUserModerator() {
        String moderatorRole = "MODERATOR";
        Admin moderator = Admin.builder().user(user).board(board).role(moderatorRole).build();
        ReflectionTestUtils.setField(moderator, "adminId", 101L);
        Admin activeModerator = Admin.builder().user(user).board(board).role(moderatorRole).build();
        ReflectionTestUtils.setField(activeModerator, "adminId", 102L);
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, moderatorRole, true))
                .thenReturn(Optional.of(activeModerator));

        assertThatThrownBy(() -> duplicatePolicy.validateNoOtherActiveAdmin(moderator, board))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void findReusableAdmin_delegatesInactiveLookup() {
        admin.deactivate();
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, Role.BOARD_ADMIN, false))
                .thenReturn(Optional.of(admin));

        assertThat(duplicatePolicy.findReusableAdmin(user, board, Role.BOARD_ADMIN))
                .containsSame(admin);
    }

    @Test
    void saveAndMapDuplicate_mapsDataIntegrityViolation() {
        when(adminRepository.saveAndFlush(admin))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> duplicatePolicy.saveAndMapDuplicate(admin))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void flushAndMapDuplicate_mapsDataIntegrityViolation() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(adminRepository)
                .flush();

        assertThatThrownBy(() -> duplicatePolicy.flushAndMapDuplicate(admin))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
        verify(adminRepository).flush();
    }
}
