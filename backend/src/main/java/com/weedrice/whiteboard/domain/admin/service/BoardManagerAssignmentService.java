package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BoardManagerAssignmentService {

    private final AdminRepository adminRepository;
    private final AdminEligibleUserService adminEligibleUserService;
    private final AdminAssignmentDuplicatePolicy duplicatePolicy;

    @Transactional
    public Admin assignBoardManager(Board board, User user) {
        adminEligibleUserService.validateActiveUser(user);

        List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true);
        Admin targetActiveManager = activeManagers.stream()
                .filter(activeManager -> Objects.equals(activeManager.getUser().getUserId(), user.getUserId()))
                .max(Comparator.comparing(Admin::getAdminId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
        if (targetActiveManager != null) {
            activeManagers.stream()
                    .filter(activeManager -> !Objects.equals(activeManager.getAdminId(), targetActiveManager.getAdminId()))
                    .forEach(Admin::deactivate);
            return duplicatePolicy.flushAndMapDuplicate(targetActiveManager);
        }

        activeManagers.forEach(Admin::deactivate);

        Admin reusableManager = duplicatePolicy.findReusableAdmin(user, board, Role.BOARD_ADMIN)
                .orElse(null);
        if (reusableManager != null) {
            reusableManager.activate();
            return duplicatePolicy.flushAndMapDuplicate(reusableManager);
        }

        return duplicatePolicy.saveAndMapDuplicate(Admin.builder()
                .user(user)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build());
    }

    @Transactional
    public Admin activateBoardManager(Admin admin, Board board) {
        adminEligibleUserService.validateActiveUser(admin.getUser());

        List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true);
        activeManagers.stream()
                .filter(activeManager -> !Objects.equals(activeManager.getAdminId(), admin.getAdminId()))
                .forEach(Admin::deactivate);

        admin.activate();
        return duplicatePolicy.flushAndMapDuplicate(admin);
    }
}
