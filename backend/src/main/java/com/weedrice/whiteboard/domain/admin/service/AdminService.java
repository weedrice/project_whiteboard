package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.DashboardStatsDto;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.dto.AdminCreateRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final IpBlockRepository ipBlockRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;

    @Transactional
    public Admin createAdmin(Long userId, Long boardId, String role) {
        SecurityUtils.validateSuperAdminPermission();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (adminRepository.existsByUser(user)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Board board = null;
        if (boardId != null) {
            board = boardRepository.findById(boardId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        }

        Admin admin = Admin.builder()
                .user(user)
                .board(board)
                .role(role)
                .build();

        if (admin == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return adminRepository.save(admin);
    }

    public List<Admin> getAllAdmins() {
        SecurityUtils.validateSuperAdminPermission();
        return adminRepository.findAll();
    }

    @Transactional
    public void deactivateAdmin(Long adminId) {
        SecurityUtils.validateSuperAdminPermission();
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        admin.deactivate();
    }

    @Transactional
    public void activateAdmin(Long adminId) {
        SecurityUtils.validateSuperAdminPermission();
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        admin.activate();
    }

    @Transactional
    public IpBlock blockIp(Long adminUserId, String ipAddress, String reason, LocalDateTime endDate) {
        SecurityUtils.validateSuperAdminPermission();

        // TODO: 이미 차단된 IP인지 확인 필요

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Admin admin = adminRepository.findByUserAndIsActive(adminUser, "Y")
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        IpBlock ipBlock = IpBlock.builder()
                .ipAddress(ipAddress)
                .reason(reason)
                .admin(admin)
                .endDate(endDate)
                .build();

        return ipBlockRepository.save(ipBlock);
    }

    @Transactional
    public void unblockIp(String ipAddress) {
        SecurityUtils.validateSuperAdminPermission();
        IpBlock ipBlock = ipBlockRepository.findByIpAddress(ipAddress)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ipBlockRepository.delete(ipBlock);
    }

    public List<IpBlock> getBlockedIps() {
        SecurityUtils.validateSuperAdminPermission();
        return ipBlockRepository.findAll();
    }

    public boolean isIpBlocked(String ipAddress) {
        return ipBlockRepository.findByIpAddressAndEndDateAfterOrEndDateIsNull(ipAddress, LocalDateTime.now())
                .isPresent();
    }

    public DashboardStatsDto getDashboardStats() {
        SecurityUtils.validateSuperAdminPermission();

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
}