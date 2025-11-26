package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;

    @Transactional
    public Admin createAdmin(Long userId, Long boardId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Board board = null;
        if (boardId != null) {
            board = boardRepository.findById(boardId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        }

        // 이미 관리자인지 확인
        if (adminRepository.existsByUserAndRoleAndIsActive(user, role, "Y")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미 해당 역할의 관리자입니다.");
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
}
