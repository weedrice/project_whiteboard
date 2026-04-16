package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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
    public AdminResponse createAdmin(String loginId, Long boardId, String role) {
        if (boardId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "boardId is required");
        }

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (Role.BOARD_ADMIN.equals(role)) {
            Admin currentManager = adminRepository
                    .findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true)
                    .orElse(null);

            if (currentManager != null && currentManager.getUser().getUserId().equals(user.getUserId())) {
                return AdminResponse.from(currentManager);
            }

            List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true);
            activeManagers.forEach(Admin::deactivate);

            Admin reusableManager = adminRepository.findByUserAndBoardAndRole(user, board, Role.BOARD_ADMIN)
                    .orElse(null);
            if (reusableManager != null) {
                reusableManager.activate();
                return AdminResponse.from(reusableManager);
            }

            Admin boardManager = Admin.builder()
                    .user(user)
                    .board(board)
                    .role(Role.BOARD_ADMIN)
                    .build();
            return AdminResponse.from(adminRepository.save(boardManager));
        }

        if (adminRepository.existsByUserAndBoardAndRoleAndIsActive(user, board, role, true)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Admin admin = Admin.builder()
                .user(user)
                .board(board)
                .role(role)
                .build();
        return AdminResponse.from(adminRepository.save(admin));
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
        return createAdmin(loginId, boardId, Role.BOARD_ADMIN);
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
        admin.activate();
    }
}
