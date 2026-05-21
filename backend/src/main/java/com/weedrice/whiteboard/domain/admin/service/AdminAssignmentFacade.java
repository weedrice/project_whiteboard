package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.AdminRole;
import com.weedrice.whiteboard.domain.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
public class AdminAssignmentFacade {

    private final AdminAssignmentService adminAssignmentService;

    public AdminResponse createAdmin(String loginId, Long boardId, AdminRole role) {
        return adminAssignmentService.createAdmin(loginId, boardId, role);
    }

    public AdminResponse getBoardManager(Long boardId) {
        return adminAssignmentService.getBoardManager(boardId);
    }

    public AdminResponse replaceBoardManager(Long boardId, String loginId) {
        return adminAssignmentService.replaceBoardManager(boardId, loginId);
    }

    public void deactivateAdmin(Long adminId) {
        adminAssignmentService.deactivateAdmin(adminId);
    }

    public void activateAdmin(Long adminId) {
        adminAssignmentService.activateAdmin(adminId);
    }
}
