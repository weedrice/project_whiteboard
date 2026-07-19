package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.OperationalPrivilegeRevocationGuard;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPrivilegeCleanupService {

    private final AdminRepository adminRepository;
    private final BoardRepository boardRepository;
    private final OperationalPrivilegeRevocationGuard privilegeRevocationGuard;

    @Transactional
    public void removeOperationalPrivileges(User user) {
        removeOperationalPrivileges(user, null, false);
    }

    @Transactional
    public void removeOperationalPrivileges(User user, Long actorUserId) {
        removeOperationalPrivileges(user, actorUserId, true);
    }

    private void removeOperationalPrivileges(User user, Long actorUserId, boolean validateActor) {
        Objects.requireNonNull(user, "user must not be null");

        List<Long> activeBoardIds = adminRepository.findActiveBoardIdsByUser(user).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (!activeBoardIds.isEmpty()
                && boardRepository.findByBoardIdInForUpdate(activeBoardIds).size() != activeBoardIds.size()) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
        var lockedActiveAdmins = adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(user, true);
        if (validateActor) {
            privilegeRevocationGuard.validateOperationalPrivilegesCanBeRevokedBy(user, lockedActiveAdmins, actorUserId);
        } else {
            privilegeRevocationGuard.validateOperationalPrivilegesCanBeRevoked(user, lockedActiveAdmins);
        }

        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            user.revokeSuperAdminRole();
        }

        lockedActiveAdmins.forEach(Admin::deactivate);
    }
}
