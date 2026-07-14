package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.AdminRole;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAssignmentService {

    private final AdminRepository adminRepository;
    private final BoardRepository boardRepository;
    private final AdminEligibleUserService adminEligibleUserService;
    private final BoardManagerAssignmentService boardManagerAssignmentService;
    private final OperationalPrivilegeRevocationGuard privilegeRevocationGuard;
    private final AdminAssignmentDuplicatePolicy duplicatePolicy;

    @Transactional
    public AdminResponse createAdmin(String loginId, Long boardId, AdminRole role) {
        if (boardId == null) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.admin.boardId.required");
        }
        if (role == null) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.admin.role.required");
        }

        String roleValue = role.name();

        var user = adminEligibleUserService.getActiveUserByLoginId(loginId);
        Board board = boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (role == AdminRole.BOARD_ADMIN) {
            return AdminResponse.from(boardManagerAssignmentService.assignBoardManager(board, user));
        }

        duplicatePolicy.validateNoActiveAdmin(user, board, roleValue);

        Admin reusableAdmin = duplicatePolicy.findReusableAdmin(user, board, roleValue)
                .orElse(null);
        if (reusableAdmin != null) {
            reusableAdmin.activate();
            return AdminResponse.from(duplicatePolicy.flushAndMapDuplicate(reusableAdmin));
        }

        Admin admin = Admin.builder()
                .user(user)
                .board(board)
                .role(roleValue)
                .build();
        return AdminResponse.from(duplicatePolicy.saveAndMapDuplicate(admin));
    }

    @Transactional(readOnly = true)
    public AdminResponse getBoardManager(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        return adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true)
                .map(AdminResponse::from)
                .orElse(null);
    }

    @Transactional
    public AdminResponse replaceBoardManager(Long boardId, String loginId) {
        return createAdmin(loginId, boardId, AdminRole.BOARD_ADMIN);
    }

    @Transactional
    public void deactivateAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (Role.BOARD_ADMIN.equals(admin.getRole()) && Boolean.TRUE.equals(admin.getIsActive())) {
            privilegeRevocationGuard.validateBoardAdminCanBeRevoked(admin);
        }
        admin.deactivate();
    }

    @Transactional
    public void activateAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Board board = boardRepository.findByIdForUpdate(admin.getBoard().getBoardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (Role.BOARD_ADMIN.equals(admin.getRole())) {
            boardManagerAssignmentService.activateBoardManager(admin, board);
            return;
        }

        adminEligibleUserService.validateActiveUser(admin.getUser());
        duplicatePolicy.validateNoOtherActiveAdmin(admin, board);
        admin.activate();
        duplicatePolicy.flushAndMapDuplicate(admin);
    }

}
