package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Admin createAdmin(Long userId, Long boardId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // SUPER 역할은 Admin 엔티티가 아닌 User 엔티티에서 관리
        if ("SUPER".equals(role)) {
            user.grantSuperAdminRole();
            userRepository.save(user);
            return null; // Admin 엔티티에 저장하지 않으므로 null 반환 또는 다른 응답
        }

        // BOARD_ADMIN, MODERATOR 역할은 boardId 필수
        if (boardId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "게시판 관리자 역할은 boardId가 필수입니다.");
        }
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        // 이미 해당 게시판의 관리자인지 확인
        if (adminRepository.existsByUserAndBoardAndRoleAndIsActive(user, board, role, "Y")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미 해당 게시판의 관리자입니다.");
        }

        Admin admin = Admin.builder()
                .user(user)
                .board(board)
                .role(role)
                .build();
        return adminRepository.save(admin);
    }

    @Transactional
    public void deactivateAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        admin.deactivate();
    }

    @Transactional
    public void activateAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        admin.activate();
    }

    public List<Admin> getAllAdmins() {
        return adminRepository.findByIsActive("Y");
    }

    @Transactional
    public IpBlock blockIp(Long adminUserId, String ipAddress, String reason, LocalDateTime endDate) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // SUPER 관리자 또는 ADMIN 역할 확인
        if (!adminUser.getIsSuperAdmin().equals("Y") &&
                !adminRepository.findByUserAndBoardAndIsActive(adminUser, null, "Y").isPresent()) { // 이 부분은 SUPER 관리자만 IP 차단 가능하도록 변경 필요
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 없습니다.");
        }

        ipBlockRepository.findById(ipAddress)
                .ifPresent(block -> {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미 차단된 IP 주소입니다.");
                });

        IpBlock ipBlock = IpBlock.builder()
                .ipAddress(ipAddress)
                .admin(null) // TODO: IP 차단 Admin 연결 로직 수정 필요
                .reason(reason)
                .startDate(LocalDateTime.now())
                .endDate(endDate)
                .build();
        return ipBlockRepository.save(ipBlock);
    }

    @Transactional
    public void unblockIp(String ipAddress) {
        IpBlock ipBlock = ipBlockRepository.findById(ipAddress)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ipBlockRepository.delete(ipBlock);
    }

    public List<IpBlock> getBlockedIps() {
        return ipBlockRepository.findByEndDateAfterOrEndDateIsNull(LocalDateTime.now());
    }

    public boolean isIpBlocked(String ipAddress) {
        return ipBlockRepository.findByIpAddressAndEndDateAfterOrEndDateIsNull(ipAddress, LocalDateTime.now()).isPresent();
    }
}
