package com.weedrice.whiteboard.domain.admin.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUserAndBoardAndIsActive(User user, Board board, Boolean isActive);
    List<Admin> findByIsActive(Boolean isActive);
    boolean existsByUserAndBoardAndRoleAndIsActive(User user, Board board, String role, Boolean isActive);
    boolean existsByUserAndRoleAndIsActive(User user, String role, Boolean isActive);
    Optional<Admin> findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(Board board, String role, Boolean isActive);
    List<Admin> findByBoardAndRoleAndIsActive(Board board, String role, Boolean isActive);
    Optional<Admin> findByUserAndBoardAndRole(User user, Board board, String role);
    Optional<Admin> findFirstByUserAndIsActiveOrderByAdminIdAsc(User user, Boolean isActive);
    List<Admin> findByUserUserIdInAndIsActiveOrderByAdminIdAsc(Collection<Long> userIds, Boolean isActive);
    @EntityGraph(attributePaths = {"user", "board"})
    List<Admin> findByBoard_BoardIdInAndRoleAndIsActiveOrderByBoard_BoardIdAscAdminIdDesc(
            Collection<Long> boardIds, String role, Boolean isActive);
    @EntityGraph(attributePaths = "board")
    List<Admin> findByUserAndBoard_BoardIdInAndIsActive(User user, Collection<Long> boardIds, Boolean isActive);
    Optional<Admin> findByUserAndIsActive(User user, Boolean isActive);
    boolean existsByUser(User user);
    void deleteByBoard(Board board);
    boolean existsByUserAndBoardAndIsActive(User userId, Board boardId, Boolean isActive);
}
