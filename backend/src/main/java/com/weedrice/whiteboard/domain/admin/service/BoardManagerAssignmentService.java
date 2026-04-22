package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BoardManagerAssignmentService {

    private final AdminRepository adminRepository;

    @Transactional
    public Admin assignBoardManager(Board board, User user) {
        requireActiveAccount(user);

        List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true);
        Admin targetActiveManager = activeManagers.stream()
                .filter(activeManager -> Objects.equals(activeManager.getUser().getUserId(), user.getUserId()))
                .max(Comparator.comparing(Admin::getAdminId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
        if (targetActiveManager != null) {
            activeManagers.stream()
                    .filter(activeManager -> !Objects.equals(activeManager.getAdminId(), targetActiveManager.getAdminId()))
                    .forEach(Admin::deactivate);
            return flushAndReturn(targetActiveManager);
        }

        activeManagers.forEach(Admin::deactivate);

        Admin reusableManager = adminRepository
                .findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(user, board, Role.BOARD_ADMIN, false)
                .orElse(null);
        if (reusableManager != null) {
            reusableManager.activate();
            return flushAndReturn(reusableManager);
        }

        return saveAndReturn(Admin.builder()
                .user(user)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build());
    }

    @Transactional
    public Admin activateBoardManager(Admin admin, Board board) {
        requireActiveAccount(admin.getUser());

        List<Admin> activeManagers = adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true);
        activeManagers.stream()
                .filter(activeManager -> !Objects.equals(activeManager.getAdminId(), admin.getAdminId()))
                .forEach(Admin::deactivate);

        admin.activate();
        return flushAndReturn(admin);
    }

    private Admin saveAndReturn(Admin admin) {
        try {
            return adminRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private Admin flushAndReturn(Admin admin) {
        try {
            adminRepository.flush();
            return admin;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private void requireActiveAccount(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }
}
