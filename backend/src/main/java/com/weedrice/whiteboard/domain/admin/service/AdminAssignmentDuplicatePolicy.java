package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class AdminAssignmentDuplicatePolicy {

    private final AdminRepository adminRepository;

    Optional<Admin> findReusableAdmin(User user, Board board, String role) {
        return adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, role, false);
    }

    void validateNoActiveAdmin(User user, Board board, String role) {
        adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                        user, board, role, true)
                .ifPresent(activeAdmin -> {
                    throw duplicate();
                });
    }

    void validateNoOtherActiveAdmin(Admin admin, Board board) {
        if (adminRepository.countByBoardAndRoleAndIsActiveAndAdminIdNot(
                board, admin.getRole(), true, admin.getAdminId()) > 0) {
            throw duplicate();
        }
    }

    Admin saveAndMapDuplicate(Admin admin) {
        try {
            return adminRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    Admin flushAndMapDuplicate(Admin admin) {
        try {
            adminRepository.flush();
            return admin;
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private BusinessException duplicate() {
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }
}
