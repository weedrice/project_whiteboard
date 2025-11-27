package com.weedrice.whiteboard.domain.admin.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUserAndIsActive(User user, String isActive);
    List<Admin> findByIsActive(String isActive);
    boolean existsByUserAndRoleAndIsActive(User user, String role, String isActive);
    Optional<Admin> findByBoardAndRole(Board board, String role);
}
