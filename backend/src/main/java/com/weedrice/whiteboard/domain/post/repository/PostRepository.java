package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {
        interface BoardPostCountProjection {
                Long getBoardId();

                Long getPostCount();
        }

        List<Post> findByCreatedAtAfterAndIsDeleted(LocalDateTime dateTime, Boolean isDeleted);
        Page<Post> findByUserAndIsDeleted(User user, Boolean isDeleted, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        Page<Post> findByAgent_AgentIdAndIsDeletedOrderByCreatedAtDesc(Long agentId, Boolean isDeleted, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        Page<Post> findByBoard_BoardIdInAndIsDeletedFalseOrderByCreatedAtDesc(List<Long> boardIds, Pageable pageable);
        long countByBoard_BoardIdAndIsDeleted(Long boardId, Boolean isDeleted);
        @Query("""
                SELECT p.board.boardId AS boardId, COUNT(p) AS postCount
                FROM Post p
                WHERE p.board.boardId IN :boardIds
                  AND p.isDeleted = false
                GROUP BY p.board.boardId
                """)
        List<BoardPostCountProjection> countActiveByBoardIds(@Param("boardIds") Collection<Long> boardIds);
        List<Post> findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(Long boardId, Boolean isNotice, Boolean isDeleted);
        @EntityGraph(attributePaths = {"user", "board", "category"})
        Page<Post> findByBoard_BoardId(Long boardId, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        List<Post> findByPostIdIn(Collection<Long> postIds);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Post p WHERE p.postId = :postId")
        java.util.Optional<Post> findByIdForUpdate(@Param("postId") Long postId);
    
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

        @Modifying(flushAutomatically = true)
        @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.postId = :postId")
        int incrementCommentCount(Long postId);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Post p
                SET p.commentCount = CASE WHEN p.commentCount > 0 THEN p.commentCount - 1 ELSE 0 END
                WHERE p.postId = :postId
                """)
        int decrementCommentCount(Long postId);

        void deleteByBoard(Board board);
}
