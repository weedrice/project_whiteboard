package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.DashboardStatsDto;
import com.weedrice.whiteboard.domain.admin.dto.IpBlockResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final IpBlockRepository ipBlockRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;

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
    @Transactional(readOnly = true)
    public List<AdminResponse> getAllAdmins() {
        return adminRepository.findAll().stream()
                .map(AdminResponse::from)
                .toList();
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

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public IpBlockResponse blockIp(Long adminUserId, String ipAddress, String reason, LocalDateTime endDate) {

        if (ipBlockRepository.findByIpAddress(ipAddress).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Admin admin = adminRepository.findFirstByUserAndIsActiveOrderByAdminIdAsc(adminUser, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        IpBlock ipBlock = IpBlock.builder()
                .ipAddress(ipAddress)
                .reason(reason)
                .admin(admin)
                .endDate(endDate)
                .build();

        return IpBlockResponse.from(ipBlockRepository.save(ipBlock));
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public void unblockIp(String ipAddress) {
        IpBlock ipBlock = ipBlockRepository.findByIpAddress(ipAddress)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ipBlockRepository.delete(ipBlock);
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional(readOnly = true)
    public List<IpBlockResponse> getBlockedIps() {
        return ipBlockRepository.findAll().stream()
                .map(IpBlockResponse::from)
                .toList();
    }

    public boolean isIpBlocked(String ipAddress) {
        return ipBlockRepository.findByIpAddressAndEndDateAfterOrEndDateIsNull(ipAddress, LocalDateTime.now())
                .isPresent();
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public DashboardStatsDto getDashboardStats() {

        long totalUsers = userRepository.count();
        long totalPosts = postRepository.count();
        long pendingReports = reportRepository.countByStatus("PENDING");
        // Active Users: Logged in within the last 24 hours
        long activeUsers = userRepository.countByLastLoginAtAfter(LocalDateTime.now().minusDays(1));

        return DashboardStatsDto.builder()
                .totalUsers(totalUsers)
                .totalPosts(totalPosts)
                .pendingReports(pendingReports)
                .activeUsers(activeUsers)
                .build();
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public SuperAdminUpdateResponse createSuperAdmin(@NotNull String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getIsSuperAdmin()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        user.grantSuperAdminRole();
        return SuperAdminUpdateResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public SuperAdminUpdateResponse deactiveSuperAdmin(@NotNull String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.getIsSuperAdmin()) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
        }

        user.revokeSuperAdminRole();
        return SuperAdminUpdateResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public List<SuperAdminResponse> getSuperAdmin() {
        List<User> userList = userRepository.findByIsSuperAdminTrue();

        return userList.stream().map(SuperAdminResponse::from).toList();
    }
}
