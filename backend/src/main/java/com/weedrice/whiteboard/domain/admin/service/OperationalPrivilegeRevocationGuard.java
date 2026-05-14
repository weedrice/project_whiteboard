package com.weedrice.whiteboard.domain.admin.service;

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
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationalPrivilegeRevocationGuard {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final BoardRepository boardRepository;

    public void validateSuperAdminCanBeRevoked(User user) {
        Objects.requireNonNull(user, "user must not be null");

        if (user.isUsableSuperAdmin() && userRepository.findUsableSuperAdminsForUpdate().size() <= 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public void validateBoardAdminCanBeRevoked(Admin admin) {
        Objects.requireNonNull(admin, "admin must not be null");

        if (!isActiveBoardAdmin(admin)) {
            return;
        }

        Board board = boardRepository.findByIdForUpdate(admin.getBoard().getBoardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        long otherActiveBoardAdmins = adminRepository.countByBoardAndRoleAndIsActiveAndAdminIdNot(
                board, Role.BOARD_ADMIN, true, admin.getAdminId());
        if (otherActiveBoardAdmins == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "At least one active board manager is required");
        }
    }

    public void validateOperationalPrivilegesCanBeRevoked(User user, Collection<Admin> activeAdmins) {
        Objects.requireNonNull(user, "user must not be null");
        validateSuperAdminCanBeRevoked(user);
        validateBoardAdminsCanBeRevoked(activeAdmins);
    }

    private void validateBoardAdminsCanBeRevoked(Collection<Admin> activeAdmins) {
        if (activeAdmins == null || activeAdmins.isEmpty()) {
            return;
        }

        List<Admin> boardAdmins = activeAdmins.stream()
                .filter(this::isActiveBoardAdmin)
                .toList();
        List<Long> boardIds = boardAdmins.stream()
                .map(Admin::getBoard)
                .filter(Objects::nonNull)
                .map(Board::getBoardId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<Long> targetAdminIds = boardAdmins.stream()
                .map(Admin::getAdminId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (boardIds.isEmpty() || targetAdminIds.isEmpty()) {
            return;
        }

        List<Board> lockedBoards = boardRepository.findByBoardIdInForUpdate(boardIds);
        if (lockedBoards.size() != boardIds.size()) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }

        Map<Long, Long> remainingAdminCounts = adminRepository.countActiveAdminsByBoardIdsExcludingAdminIds(
                        boardIds,
                        Role.BOARD_ADMIN,
                        true,
                        targetAdminIds)
                .stream()
                .collect(Collectors.toMap(
                        AdminRepository.BoardAdminCountProjection::getBoardId,
                        AdminRepository.BoardAdminCountProjection::getAdminCount));

        for (Long boardId : boardIds) {
            if (remainingAdminCounts.getOrDefault(boardId, 0L) == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "At least one active board manager is required");
            }
        }
    }

    private boolean isActiveBoardAdmin(Admin admin) {
        return admin != null
                && Role.BOARD_ADMIN.equals(admin.getRole())
                && Boolean.TRUE.equals(admin.getIsActive());
    }
}
