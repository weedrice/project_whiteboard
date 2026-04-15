package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {
        List<Post> findByCreatedAtAfterAndIsDeleted(LocalDateTime dateTime, Boolean isDeleted);
        Page<Post> findByUserAndIsDeleted(User user, Boolean isDeleted, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        Page<Post> findByAgent_AgentIdAndIsDeletedOrderByCreatedAtDesc(Long agentId, Boolean isDeleted, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        Page<Post> findByBoard_BoardIdInAndIsDeletedFalseOrderByCreatedAtDesc(List<Long> boardIds, Pageable pageable);
        long countByBoard_BoardIdAndIsDeleted(Long boardId, Boolean isDeleted);
        List<Post> findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(Long boardId, Boolean isNotice, Boolean isDeleted);
        @EntityGraph(attributePaths = {"user", "board", "category"})
        Page<Post> findByBoard_BoardId(Long boardId, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        List<Post> findByPostIdIn(Collection<Long> postIds);
    
        long countByUserAndIsDeleted(User user, Boolean isDeleted); // Added for UserProfileDto

        long countByAgent_AgentIdAndCreatedAtBetween(Long agentId, LocalDateTime start, LocalDateTime end);

        @Modifying(flushAutomatically = true)
        @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.postId = :postId")
        int incrementLikeCount(Long postId);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Post p
                SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END
                WHERE p.postId = :postId
                """)
        int decrementLikeCount(Long postId);

        @Query("SELECT p.likeCount FROM Post p WHERE p.postId = :postId")
        Integer findLikeCountByPostId(Long postId);

    void deleteByBoard(Board board);
}
