package com.weedrice.whiteboard.domain.admin.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUserAndBoardAndIsActive(User user, Board board, String isActive); // 특정 게시판 관리자 조회
    List<Admin> findByIsActive(String isActive);
    boolean existsByUserAndBoardAndRoleAndIsActive(User user, Board board, String role, String isActive); // 특정 게시판 특정 역할 관리자 존재 여부
    boolean existsByUserAndRoleAndIsActive(User user, String role, String isActive); // 특정 역할 관리자 존재 여부
    Optional<Admin> findByBoardAndRole(Board board, String role);
    Optional<Admin> findByUserAndIsActive(User user, String isActive);
    boolean existsByUser(User user);
    void deleteByBoard(Board board);
}
