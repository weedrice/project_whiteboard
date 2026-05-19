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
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {
        interface BoardPostCountProjection {
                Long getBoardId();

                Long getPostCount();
        }

        interface PublicLandingPostStatsProjection {
                Long getTotalPosts();

                Long getPostsToday();

                Long getPostsYesterday();
        }

        interface AgentPrimaryBoardProjection {
                Long getBoardId();

                String getBoardName();

                String getBoardUrl();

                Long getActivityCount();
        }

        List<Post> findByCreatedAtAfterAndIsDeleted(LocalDateTime dateTime, Boolean isDeleted);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        Page<Post> findByUserAndIsDeleted(User user, Boolean isDeleted, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        Page<Post> findByUserOrderByCreatedAtDescPostIdDesc(User user, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        Page<Post> findByAgent_AgentIdAndIsDeleted(Long agentId, Boolean isDeleted, Pageable pageable);
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
        @Query("""
                SELECT b.boardId AS boardId, COUNT(p) AS postCount
                FROM Post p
                JOIN p.board b
                WHERE b.boardId IN :boardIds
                  AND p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                GROUP BY b.boardId
                """)
        List<BoardPostCountProjection> countPublicVisiblePostsByBoardIds(@Param("boardIds") Collection<Long> boardIds);
        @Query("""
                SELECT COUNT(p)
                FROM Post p
                WHERE p.isDeleted = false
                """)
        long countVisiblePostsForAdminDashboard();

        @Query("""
                SELECT COUNT(p)
                FROM Post p
                JOIN p.board b
                WHERE p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                  AND LOWER(b.boardUrl) <> :inquiryBoardUrl
                """)
        long countPublicLandingVisiblePosts(@Param("inquiryBoardUrl") String inquiryBoardUrl);

        @Query("""
                SELECT
                    COUNT(p) AS totalPosts,
                    COALESCE(SUM(CASE
                        WHEN p.createdAt >= :todayStart AND p.createdAt < :tomorrowStart
                        THEN 1L ELSE 0L
                    END), 0L) AS postsToday,
                    COALESCE(SUM(CASE
                        WHEN p.createdAt >= :yesterdayStart AND p.createdAt < :todayStart
                        THEN 1L ELSE 0L
                    END), 0L) AS postsYesterday
                FROM Post p
                JOIN p.board b
                WHERE p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                  AND LOWER(b.boardUrl) <> :inquiryBoardUrl
                """)
        PublicLandingPostStatsProjection countPublicLandingPostStats(
                @Param("todayStart") LocalDateTime todayStart,
                @Param("tomorrowStart") LocalDateTime tomorrowStart,
                @Param("yesterdayStart") LocalDateTime yesterdayStart,
                @Param("inquiryBoardUrl") String inquiryBoardUrl);

        @Query("""
                SELECT COUNT(p)
                FROM Post p
                JOIN p.board b
                WHERE p.createdAt >= :start
                  AND p.createdAt < :end
                  AND p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                  AND LOWER(b.boardUrl) <> :inquiryBoardUrl
                """)
        long countPublicLandingVisiblePostsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                @Param("start") LocalDateTime start,
                @Param("end") LocalDateTime end,
                @Param("inquiryBoardUrl") String inquiryBoardUrl);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        List<Post> findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(Long boardId, Boolean isNotice, Boolean isDeleted);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        Page<Post> findByBoard_BoardIdAndIsDeletedFalse(Long boardId, Pageable pageable);
        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        List<Post> findByPostIdIn(Collection<Long> postIds);
        @EntityGraph(attributePaths = {"user", "agent", "board", "board.creator", "category"})
        List<Post> findByPostIdInAndIsDeletedFalse(Collection<Long> postIds);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Post p WHERE p.postId = :postId")
        Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                SELECT p
                FROM Post p
                WHERE p.postId = :postId
                  AND p.board.boardId = :boardId
                  AND p.isDeleted = false
                """)
        Optional<Post> findActiveByIdAndBoardIdForUpdate(
                @Param("postId") Long postId,
                @Param("boardId") Long boardId);

        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Post p WHERE p.postId = :postId")
        Optional<Post> findByIdWithRelationsForUpdate(@Param("postId") Long postId);
    
        long countByUserAndIsDeleted(User user, Boolean isDeleted);

        @Query("""
                SELECT COUNT(p)
                FROM Post p
                JOIN p.board b
                WHERE p.user = :user
                  AND p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                """)
        long countPublicProfilePostsByUser(@Param("user") User user);

        long countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                Long agentId,
                LocalDateTime start,
                LocalDateTime end);
        @Query("""
                SELECT COUNT(p)
                FROM Post p
                JOIN p.board b
                WHERE p.agent.agentId = :agentId
                  AND p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                  AND b.agentUseYn = true
                """)
        long countPublicProfilePostsByAgentId(@Param("agentId") Long agentId);

        @Query("""
                SELECT COALESCE(SUM(p.likeCount), 0)
                FROM Post p
                JOIN p.board b
                WHERE p.agent.agentId = :agentId
                  AND p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                  AND b.agentUseYn = true
                """)
        long sumPublicProfilePostLikesByAgentId(@Param("agentId") Long agentId);

        @EntityGraph(attributePaths = {"user", "agent", "board", "category"})
        @Query("""
                SELECT p
                FROM Post p
                JOIN p.board b
                WHERE p.agent.agentId = :agentId
                  AND p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                  AND b.agentUseYn = true
                ORDER BY p.createdAt DESC, p.postId DESC
                """)
        Page<Post> findPublicProfilePostsByAgentId(@Param("agentId") Long agentId, Pageable pageable);

        @Query("""
                SELECT b.boardId AS boardId,
                       b.boardName AS boardName,
                       b.boardUrl AS boardUrl,
                       COUNT(p) AS activityCount
                FROM Post p
                JOIN p.board b
                WHERE p.agent.agentId = :agentId
                  AND p.isDeleted = false
                  AND p.isSecret = false
                  AND b.isActive = true
                  AND b.isPublic = true
                  AND b.agentUseYn = true
                GROUP BY b.boardId, b.boardName, b.boardUrl
                ORDER BY COUNT(p) DESC, b.boardId ASC
                """)
        List<AgentPrimaryBoardProjection> findPrimaryBoardsByAgentPosts(@Param("agentId") Long agentId, Pageable pageable);
        long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(LocalDateTime start, LocalDateTime end);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Post p
                SET p.likeCount = p.likeCount + 1
                WHERE p.postId = :postId
                  AND p.isDeleted = false
                """)
        int incrementLikeCount(Long postId);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Post p
                SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END
                WHERE p.postId = :postId
                  AND p.isDeleted = false
                """)
        int decrementLikeCount(Long postId);

        @Query("SELECT p.likeCount FROM Post p WHERE p.postId = :postId AND p.isDeleted = false")
        Integer findLikeCountByPostId(Long postId);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Post p
                SET p.viewCount = p.viewCount + 1
                WHERE p.postId = :postId
                """)
        int incrementViewCount(@Param("postId") Long postId);

        @Query("SELECT p.viewCount FROM Post p WHERE p.postId = :postId")
        Integer findViewCountByPostId(@Param("postId") Long postId);

        @Modifying(flushAutomatically = true)
        @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.postId = :postId AND p.isDeleted = false")
        int incrementCommentCount(Long postId);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Post p
                SET p.commentCount = CASE WHEN p.commentCount > 0 THEN p.commentCount - 1 ELSE 0 END
                WHERE p.postId = :postId
                  AND p.isDeleted = false
                """)
        int decrementCommentCount(Long postId);

        void deleteByBoard(Board board);
}
