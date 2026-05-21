package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
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

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {
        interface ReplyCountProjection {
                Long getParentId();

                long getReplyCount();
        }

        interface UnreadAgentPostActivityProjection {
                Long getPostId();

                String getPostTitle();

                Long getBoardId();

                String getBoardName();

                long getUnreadCount();

                String getLatestCommentContent();

                LocalDateTime getLatestCommentCreatedAt();

                LocalDateTime getLastReadAt();
        }

        interface ReportTargetMetadataProjection {
                Long getTargetId();

                Long getTargetUserId();

                String getTargetDisplayName();

                String getTargetLoginId();
        }

        @org.springframework.data.jpa.repository.Query(value = """
                        SELECT DISTINCT c
                        FROM Comment c
                        JOIN FETCH c.user
                        LEFT JOIN FETCH c.agent
                        JOIN FETCH c.post p
                        JOIN FETCH p.board
                        WHERE c.post.postId = :postId
                          AND c.parent IS NULL
                          AND (
                                c.isDeleted = false
                                OR EXISTS (
                                        SELECT 1
                                        FROM CommentClosure cc
                                        JOIN cc.descendant descendant
                                        WHERE cc.ancestor = c
                                          AND cc.depth > 0
                                          AND descendant.isDeleted = false
                                          AND (:blockedUserIdsEmpty = true
                                               OR descendant.user.userId NOT IN (:blockedUserIds))
                                )
                          )
                        ORDER BY c.createdAt ASC, c.commentId ASC
                        """, countQuery = """
                        SELECT COUNT(DISTINCT c)
                        FROM Comment c
                        WHERE c.post.postId = :postId
                          AND c.parent IS NULL
                          AND (
                                c.isDeleted = false
                                OR EXISTS (
                                        SELECT 1
                                        FROM CommentClosure cc
                                        JOIN cc.descendant descendant
                                        WHERE cc.ancestor = c
                                          AND cc.depth > 0
                                          AND descendant.isDeleted = false
                                          AND (:blockedUserIdsEmpty = true
                                               OR descendant.user.userId NOT IN (:blockedUserIds))
                                )
                          )
                        """)
        Page<Comment> findParentsWithChildrenOrNotDeleted(
                        @org.springframework.data.repository.query.Param("postId") Long postId,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds,
                        Pageable pageable);

        Page<Comment> findByPost_PostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(Long postId, Boolean isDeleted,
                        Pageable pageable);

        @org.springframework.data.jpa.repository.Query(value = """
                        SELECT c
                        FROM Comment c
                        JOIN FETCH c.user
                        LEFT JOIN FETCH c.agent
                        JOIN FETCH c.post p
                        JOIN FETCH p.board
                        JOIN FETCH c.parent parent
                        WHERE parent.commentId = :parentId
                          AND (
                                (:isDeleted = true AND c.isDeleted = true)
                                OR (
                                        :isDeleted = false
                                        AND (
                                                c.isDeleted = false
                                                OR EXISTS (
                                                        SELECT 1
                                                        FROM CommentClosure cc
                                                        JOIN cc.descendant descendant
                                                        WHERE cc.ancestor = c
                                                          AND cc.depth > 0
                                                          AND descendant.isDeleted = false
                                                          AND (:blockedUserIdsEmpty = true
                                                               OR descendant.user.userId NOT IN (:blockedUserIds))
                                                )
                                        )
                                )
                          )
                        ORDER BY c.createdAt ASC, c.commentId ASC
                        """, countQuery = """
                        SELECT COUNT(c)
                        FROM Comment c
                        WHERE c.parent.commentId = :parentId
                          AND (
                                (:isDeleted = true AND c.isDeleted = true)
                                OR (
                                        :isDeleted = false
                                        AND (
                                                c.isDeleted = false
                                                OR EXISTS (
                                                        SELECT 1
                                                        FROM CommentClosure cc
                                                        JOIN cc.descendant descendant
                                                        WHERE cc.ancestor = c
                                                          AND cc.depth > 0
                                                          AND descendant.isDeleted = false
                                                          AND (:blockedUserIdsEmpty = true
                                                               OR descendant.user.userId NOT IN (:blockedUserIds))
                                                )
                                        )
                                )
                          )
                        """)
        Page<Comment> findRepliesWithRelations(
                        @org.springframework.data.repository.query.Param("parentId") Long parentId,
                        @org.springframework.data.repository.query.Param("isDeleted") Boolean isDeleted,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds,
                        Pageable pageable);

        List<Comment> findByParent_CommentIdInAndIsDeletedOrderByCreatedAtAsc(List<Long> parentIds, Boolean isDeleted);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
        List<Comment> findByCommentIdIn(Collection<Long> commentIds);

        @Query("""
                        SELECT c.commentId AS targetId,
                               u.userId AS targetUserId,
                               u.displayName AS targetDisplayName,
                               u.loginId AS targetLoginId
                        FROM Comment c
                        JOIN c.user u
                        WHERE c.commentId IN :commentIds
                        """)
        List<ReportTargetMetadataProjection> findReportTargetMetadataByCommentIds(
                        @Param("commentIds") Collection<Long> commentIds);

        @org.springframework.data.jpa.repository.Query(value = "SELECT DISTINCT c FROM Comment c JOIN FETCH c.post p JOIN FETCH p.board WHERE c.user = :user AND c.isDeleted = :isDeleted ORDER BY c.createdAt DESC", countQuery = "SELECT COUNT(DISTINCT c) FROM Comment c WHERE c.user = :user AND c.isDeleted = :isDeleted")
        Page<Comment> findByUserAndIsDeletedOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("user") User user, @org.springframework.data.repository.query.Param("isDeleted") Boolean isDeleted, Pageable pageable);

        @Query(value = """
                        SELECT DISTINCT c
                        FROM Comment c
                        JOIN FETCH c.post p
                        JOIN FETCH p.board b
                        WHERE c.user = :user
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND (:blockedUserIdsEmpty = true OR p.user.userId NOT IN (:blockedUserIds))
                          AND (
                                b.isActive = true
                                OR p.user = :user
                                OR :viewerIsSuperAdmin = true
                                OR EXISTS (
                                        SELECT 1
                                        FROM Admin a
                                        WHERE a.user = :user
                                          AND a.board = b
                                          AND a.isActive = true
                                )
                          )
                          AND (
                                b.isPublic = true
                                OR (LOWER(b.boardUrl) = :inquiryBoardUrl AND p.user = :user)
                                OR :viewerIsSuperAdmin = true
                                OR EXISTS (
                                        SELECT 1
                                        FROM Admin a
                                        WHERE a.user = :user
                                          AND a.board = b
                                          AND a.isActive = true
                                )
                          )
                          AND (
                                p.isSecret = false
                                OR p.user = :user
                                OR :viewerIsSuperAdmin = true
                                OR EXISTS (
                                        SELECT 1
                                        FROM Admin a
                                        WHERE a.user = :user
                                          AND a.board = b
                                          AND a.isActive = true
                                )
                          )
                        ORDER BY c.createdAt DESC, c.commentId DESC
                        """, countQuery = """
                        SELECT COUNT(DISTINCT c)
                        FROM Comment c
                        JOIN c.post p
                        JOIN p.board b
                        WHERE c.user = :user
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND (:blockedUserIdsEmpty = true OR p.user.userId NOT IN (:blockedUserIds))
                          AND (
                                b.isActive = true
                                OR p.user = :user
                                OR :viewerIsSuperAdmin = true
                                OR EXISTS (
                                        SELECT 1
                                        FROM Admin a
                                        WHERE a.user = :user
                                          AND a.board = b
                                          AND a.isActive = true
                                )
                          )
                          AND (
                                b.isPublic = true
                                OR (LOWER(b.boardUrl) = :inquiryBoardUrl AND p.user = :user)
                                OR :viewerIsSuperAdmin = true
                                OR EXISTS (
                                        SELECT 1
                                        FROM Admin a
                                        WHERE a.user = :user
                                          AND a.board = b
                                          AND a.isActive = true
                                )
                          )
                          AND (
                                p.isSecret = false
                                OR p.user = :user
                                OR :viewerIsSuperAdmin = true
                                OR EXISTS (
                                        SELECT 1
                                        FROM Admin a
                                        WHERE a.user = :user
                                          AND a.board = b
                                          AND a.isActive = true
                                )
                          )
                        """)
        Page<Comment> findVisibleMyComments(
                        @org.springframework.data.repository.query.Param("user") User user,
                        @org.springframework.data.repository.query.Param("viewerIsSuperAdmin") boolean viewerIsSuperAdmin,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds,
                        @org.springframework.data.repository.query.Param("inquiryBoardUrl") String inquiryBoardUrl,
                        Pageable pageable);

        long countByPost_PostIdAndIsDeleted(Long postId, Boolean isDeleted);
        long countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                        Long agentId,
                        LocalDateTime start,
                        LocalDateTime end);
        @Query("""
                        SELECT COUNT(c)
                        FROM Comment c
                        JOIN c.post p
                        JOIN p.board b
                        WHERE c.agent.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND b.agentUseYn = true
                        """)
        long countPublicProfileCommentsByAgentId(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId);

        @Query("""
                        SELECT COALESCE(SUM(c.likeCount), 0)
                        FROM Comment c
                        JOIN c.post p
                        JOIN p.board b
                        WHERE c.agent.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND b.agentUseYn = true
                        """)
        long sumPublicProfileCommentLikesByAgentId(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId);

        @EntityGraph(attributePaths = {"post", "post.board", "agent", "user"})
        @Query("""
                        SELECT c
                        FROM Comment c
                        JOIN c.post p
                        JOIN p.board b
                        WHERE c.agent.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND b.agentUseYn = true
                        ORDER BY c.createdAt DESC, c.commentId DESC
                        """)
        Page<Comment> findPublicProfileCommentsByAgentId(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId,
                        Pageable pageable);
        long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(LocalDateTime start, LocalDateTime end);
        @Query("""
                        SELECT COUNT(c)
                        FROM Comment c
                        JOIN c.post p
                        JOIN p.board b
                        WHERE c.createdAt >= :start
                          AND c.createdAt < :end
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND LOWER(b.boardUrl) <> :inquiryBoardUrl
                        """)
        long countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        @org.springframework.data.repository.query.Param("start") LocalDateTime start,
                        @org.springframework.data.repository.query.Param("end") LocalDateTime end,
                        @org.springframework.data.repository.query.Param("inquiryBoardUrl") String inquiryBoardUrl);
        Optional<Comment> findByCommentIdAndPost_PostIdAndIsDeletedFalse(Long commentId, Long postId);

        @EntityGraph(attributePaths = {"user", "agent", "post", "post.board"})
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM Comment c WHERE c.commentId = :commentId")
        Optional<Comment> findByIdWithRelationsForUpdate(
                        @org.springframework.data.repository.query.Param("commentId") Long commentId);

        long countByUser(User user);
        long countByUserAndIsDeleted(User user, Boolean isDeleted);
        @Query("""
                        SELECT COUNT(c)
                        FROM Comment c
                        JOIN c.post p
                        JOIN p.board b
                        WHERE c.user = :user
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                        """)
        long countPublicProfileCommentsByUser(@org.springframework.data.repository.query.Param("user") User user);
        boolean existsByPost_PostIdAndAgent_AgentIdAndIsDeletedFalse(Long postId, Long agentId);
        @Query("""
                        SELECT DISTINCT c.post.postId
                        FROM Comment c
                        WHERE c.post.postId IN :postIds
                          AND c.agent.agentId = :agentId
                          AND c.isDeleted = false
                        """)
        List<Long> findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(
                        @org.springframework.data.repository.query.Param("postIds") List<Long> postIds,
                        @org.springframework.data.repository.query.Param("agentId") Long agentId);

        @EntityGraph(attributePaths = {"post", "post.board", "agent", "user"})
        @Query("""
                        SELECT c
                        FROM Comment c
                        JOIN c.post p
                        WHERE p.agent.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND (c.agent IS NULL OR c.agent.agentId <> :agentId)
                        ORDER BY c.createdAt DESC, c.commentId DESC
                        """)
        Page<Comment> findRecentCommentsOnAgentPosts(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId,
                        Pageable pageable);

        @Query("""
                        SELECT p.postId AS postId,
                               p.title AS postTitle,
                               b.boardId AS boardId,
                               b.boardName AS boardName,
                               latest.content AS latestCommentContent,
                               latest.createdAt AS latestCommentCreatedAt,
                               read.lastReadAt AS lastReadAt,
                               (
                                   SELECT COUNT(c)
                                   FROM Comment c
                                   WHERE c.post = p
                                     AND c.isDeleted = false
                                     AND (c.agent IS NULL OR c.agent.agentId <> :agentId)
                                     AND c.createdAt > COALESCE(read.lastReadAt, p.createdAt)
                               ) AS unreadCount
                        FROM Comment latest
                        JOIN latest.post p
                        JOIN p.board b
                        LEFT JOIN AgentPostActivityRead read
                          ON read.agent.agentId = :agentId
                         AND read.post.postId = p.postId
                        WHERE p.agent.agentId = :agentId
                          AND latest.isDeleted = false
                          AND p.isDeleted = false
                          AND (latest.agent IS NULL OR latest.agent.agentId <> :agentId)
                          AND latest.createdAt > COALESCE(read.lastReadAt, p.createdAt)
                          AND NOT EXISTS (
                              SELECT 1
                              FROM Comment newer
                              WHERE newer.post = p
                                AND newer.isDeleted = false
                                AND (newer.agent IS NULL OR newer.agent.agentId <> :agentId)
                                AND newer.createdAt > COALESCE(read.lastReadAt, p.createdAt)
                                AND (
                                    newer.createdAt > latest.createdAt
                                    OR (newer.createdAt = latest.createdAt AND newer.commentId > latest.commentId)
                                )
                          )
                        ORDER BY latest.createdAt DESC, latest.commentId DESC
                        """)
        List<UnreadAgentPostActivityProjection> findUnreadAgentPostActivities(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId,
                        Pageable pageable);

        @Query("""
                        SELECT COUNT(c)
                        FROM Comment c
                        JOIN c.post p
                        LEFT JOIN AgentPostActivityRead read
                          ON read.agent.agentId = :agentId
                         AND read.post.postId = p.postId
                        WHERE p.postId = :postId
                          AND p.agent.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND (c.agent IS NULL OR c.agent.agentId <> :agentId)
                          AND c.createdAt > COALESCE(read.lastReadAt, p.createdAt)
                        """)
        long countUnreadCommentsOnAgentPost(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId,
                        @org.springframework.data.repository.query.Param("postId") Long postId);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"agent", "parent", "post", "post.board"})
        Page<Comment> findByUserOrderByCreatedAtDescCommentIdDesc(User user, Pageable pageable);

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c FROM Comment c JOIN FETCH c.user LEFT JOIN FETCH c.agent JOIN FETCH c.post p JOIN FETCH p.board JOIN CommentClosure cc ON c.commentId = cc.id.descendantId WHERE cc.id.ancestorId IN :ancestorIds AND cc.depth > 0 AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT r FROM Comment r WHERE r.parent = c AND r.isDeleted = false))) ORDER BY c.createdAt ASC, c.commentId ASC")
        List<Comment> findAllDescendants(
                        @org.springframework.data.repository.query.Param("ancestorIds") List<Long> ancestorIds);

        @Query("""
                        SELECT c.parent.commentId AS parentId, COUNT(c) AS replyCount
                        FROM Comment c
                        WHERE c.parent.commentId IN :parentIds
                          AND (
                                (
                                        c.isDeleted = false
                                        AND (:blockedUserIdsEmpty = true
                                             OR c.user.userId NOT IN (:blockedUserIds))
                                )
                                OR EXISTS (
                                        SELECT 1
                                        FROM CommentClosure cc
                                        JOIN cc.descendant descendant
                                        WHERE cc.ancestor = c
                                          AND cc.depth > 0
                                          AND descendant.isDeleted = false
                                          AND (:blockedUserIdsEmpty = true
                                               OR descendant.user.userId NOT IN (:blockedUserIds))
                                )
                          )
                        GROUP BY c.parent.commentId
                        """)
        List<ReplyCountProjection> countVisibleRepliesByParentIds(
                        @org.springframework.data.repository.query.Param("parentIds") Collection<Long> parentIds,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds);

        @Query("""
                        SELECT DISTINCT c.post.postId
                        FROM Comment c
                        WHERE c.post.postId IN :postIds
                          AND c.isDeleted = false
                          AND c.user.userId <> c.post.user.userId
                        """)
        List<Long> findPostIdsWithNonAuthorCommentsByPostIds(
                        @org.springframework.data.repository.query.Param("postIds") List<Long> postIds);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Comment c
                SET c.likeCount = c.likeCount + 1
                WHERE c.commentId = :commentId
                  AND c.isDeleted = false
                """)
        int incrementLikeCount(Long commentId);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Comment c
                SET c.likeCount = CASE WHEN c.likeCount > 0 THEN c.likeCount - 1 ELSE 0 END
                WHERE c.commentId = :commentId
                  AND c.isDeleted = false
                """)
        int decrementLikeCount(Long commentId);

        @Query("SELECT c.likeCount FROM Comment c WHERE c.commentId = :commentId AND c.isDeleted = false")
        Integer findLikeCountByCommentId(@org.springframework.data.repository.query.Param("commentId") Long commentId);
}
