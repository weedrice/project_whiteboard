package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {
        List<Post> findByCreatedAtAfterAndIsDeleted(LocalDateTime dateTime, Boolean isDeleted);
        Page<Post> findByUserAndIsDeleted(User user, Boolean isDeleted, Pageable pageable);
        List<Post> findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(Long boardId, Boolean isNotice, Boolean isDeleted);
        @EntityGraph(attributePaths = {"user", "board", "category"})
        Page<Post> findByBoard_BoardId(Long boardId, Pageable pageable);
    
        long countByUserAndIsDeleted(User user, Boolean isDeleted); // Added for UserProfileDto

        long countByAgent_AgentIdAndCreatedAtBetween(Long agentId, LocalDateTime start, LocalDateTime end);

    void deleteByBoard(Board board);
}
