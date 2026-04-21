package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.AdminRole;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAssignmentService {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public AdminResponse createAdmin(String loginId, Long boardId, AdminRole role) {
        if (boardId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "boardId is required");
        }
        if (role == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "role is required");
        }

        String roleValue = role.name();

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Board board = boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (role == AdminRole.BOARD_ADMIN) {
            return assignBoardAdmin(user, board);
        }

        Admin activeAdmin = adminRepository
                .findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(user, board, roleValue, true)
                .orElse(null);
        if (activeAdmin != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Admin reusableAdmin = adminRepository
                .findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(user, board, roleValue, false)
                .orElse(null);
        if (reusableAdmin != null) {
            reusableAdmin.activate();
            return flushAndMapDuplicate(reusableAdmin);
        }

        Admin admin = Admin.builder()
                .user(user)
                .board(board)
                .role(roleValue)
                .build();
        return saveAndMapDuplicate(admin);
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional(readOnly = true)
    public AdminResponse getBoardManager(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        return adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true)
                .map(AdminResponse::from)
                .orElse(null);
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public AdminResponse replaceBoardManager(Long boardId, String loginId) {
        return createAdmin(loginId, boardId, AdminRole.BOARD_ADMIN);
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public void deactivateAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        admin.deactivate();
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public void activateAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Board board = boardRepository.findByIdForUpdate(admin.getBoard().getBoardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (Role.BOARD_ADMIN.equals(admin.getRole())) {
            List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(
                    board,
                    Role.BOARD_ADMIN,
                    true);
            activeManagers.stream()
                    .filter(activeManager -> !activeManager.getAdminId().equals(admin.getAdminId()))
                    .forEach(Admin::deactivate);
        } else {
            Admin activeAdmin = adminRepository
                    .findByUserAndBoardAndRoleAndIsActive(admin.getUser(), board, admin.getRole(), true)
                    .orElse(null);
            if (activeAdmin != null && !activeAdmin.getAdminId().equals(admin.getAdminId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
        }
        admin.activate();
        flushAndMapDuplicate(admin);
    }

    private AdminResponse assignBoardAdmin(User user, Board board) {
        List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true);
        Admin targetActiveManager = activeManagers.stream()
                .filter(activeManager -> activeManager.getUser().getUserId().equals(user.getUserId()))
                .max((left, right) -> Long.compare(left.getAdminId(), right.getAdminId()))
                .orElse(null);
        if (targetActiveManager != null) {
            activeManagers.stream()
                    .filter(activeManager -> !activeManager.getAdminId().equals(targetActiveManager.getAdminId()))
                    .forEach(Admin::deactivate);
            return flushAndMapDuplicate(targetActiveManager);
        }

        activeManagers.forEach(Admin::deactivate);

        Admin reusableManager = adminRepository
                .findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(user, board, Role.BOARD_ADMIN, false)
                .orElse(null);
        if (reusableManager != null) {
            reusableManager.activate();
            return flushAndMapDuplicate(reusableManager);
        }

        Admin boardManager = Admin.builder()
                .user(user)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();
        return saveAndMapDuplicate(boardManager);
    }

    private AdminResponse saveAndMapDuplicate(Admin admin) {
        try {
            return AdminResponse.from(adminRepository.saveAndFlush(admin));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private AdminResponse flushAndMapDuplicate(Admin admin) {
        try {
            adminRepository.flush();
            return AdminResponse.from(admin);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }
}
