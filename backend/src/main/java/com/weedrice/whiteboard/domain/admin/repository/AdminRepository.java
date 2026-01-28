package com.weedrice.whiteboard.domain.admin.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUserAndBoardAndIsActive(User user, Board board, Boolean isActive); // 특정 게시판 관리자 조회
    List<Admin> findByIsActive(Boolean isActive);
    boolean existsByUserAndBoardAndRoleAndIsActive(User user, Board board, String role, Boolean isActive); // 특정 게시판 특정 역할 관리자 존재 여부
    boolean existsByUserAndRoleAndIsActive(User user, String role, Boolean isActive); // 특정 역할 관리자 존재 여부
    Optional<Admin> findByBoardAndRole(Board board, String role);
    /** 한 User가 여러 게시판 관리자(Admin)일 수 있으므로, 첫 번째 활성 Admin만 반환 (N:1 관계 대응) */
    Optional<Admin> findFirstByUserAndIsActiveOrderByAdminIdAsc(User user, Boolean isActive);
    /** @deprecated 한 User에 여러 Admin이 있을 수 있어 다수 행 시 예외 발생. {@link #findFirstByUserAndIsActiveOrderByAdminIdAsc} 사용 권장 */
    Optional<Admin> findByUserAndIsActive(User user, Boolean isActive);
    boolean existsByUser(User user);
    void deleteByBoard(Board board);
    boolean existsByUserAndBoardAndIsActive(User userId, Board boardId, Boolean isActive);
}
