package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.domain.actor.PostIdRef;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
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

        interface UserCommentCountProjection {
                Long getUserId();

                Long getCommentCount();
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

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM Comment c WHERE c.commentId = :commentId")
        Optional<Comment> findByIdWithRelationsForBlindUpdate(@Param("commentId") Long commentId);

        @Query(
                        value = CommentThreadQueries.ROOT_CONTENT + CommentThreadQueries.OLDEST_ORDER,
                        countQuery = CommentThreadQueries.ROOT_COUNT)
        Page<Comment> findParentsWithChildrenOrNotDeleted(
                        @org.springframework.data.repository.query.Param("postId") Long postId,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds,
                        Pageable pageable);

        @Query(
                        value = CommentThreadQueries.ROOT_CONTENT + CommentThreadQueries.NEWEST_ORDER,
                        countQuery = CommentThreadQueries.ROOT_COUNT)
        Page<Comment> findParentsWithChildrenOrNotDeletedOrderByCreatedAtDesc(
                        @org.springframework.data.repository.query.Param("postId") Long postId,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds,
                        Pageable pageable);

        @Query(
                        value = CommentThreadQueries.ROOT_CONTENT + CommentThreadQueries.LIKE_ORDER,
                        countQuery = CommentThreadQueries.ROOT_COUNT)
        Page<Comment> findParentsWithChildrenOrNotDeletedOrderByLikeCount(
                        @org.springframework.data.repository.query.Param("postId") Long postId,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds,
                        Pageable pageable);

        @org.springframework.data.jpa.repository.Query("""
                        SELECT c
                        FROM Comment c
                        WHERE c.postId = :postId
                          AND c.parent IS NULL
                          AND c.isDeleted = false
                          AND c.likeCount >= :minLikes
                          AND (:blockedUserIdsEmpty = true OR c.userId NOT IN (:blockedUserIds))
                        ORDER BY c.likeCount DESC, c.createdAt ASC, c.commentId ASC
                        """)
        List<Comment> findBestRootComments(
                        @org.springframework.data.repository.query.Param("postId") Long postId,
                        @org.springframework.data.repository.query.Param("minLikes") int minLikes,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds,
                        Pageable pageable);

        Page<Comment> findByPostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(Long postId, Boolean isDeleted,
                        Pageable pageable);
        default Page<Comment> findByPost_PostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(
                        Long postId, Boolean isDeleted, Pageable pageable) {
                return findByPostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(postId, isDeleted, pageable);
        }

        @org.springframework.data.jpa.repository.Query(value = """
                        SELECT c
                        FROM Comment c
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
                                                               OR descendant.userId NOT IN (:blockedUserIds))
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
                                                               OR descendant.userId NOT IN (:blockedUserIds))
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

        List<Comment> findByCommentIdIn(Collection<Long> commentIds);

        List<Comment> findByCommentIdInAndIsDeletedFalse(Collection<Long> commentIds);

        @Query("""
                        SELECT c.commentId AS targetId,
                               u.userId AS targetUserId,
                               u.displayName AS targetDisplayName,
                               u.loginId AS targetLoginId
                        FROM Comment c, User u
                        WHERE u.userId = c.userId
                          AND c.commentId IN :commentIds
                        """)
        List<ReportTargetMetadataProjection> findReportTargetMetadataByCommentIds(
                        @Param("commentIds") Collection<Long> commentIds);

        @org.springframework.data.jpa.repository.Query(value = "SELECT DISTINCT c FROM Comment c WHERE c.userId = :userId AND c.isDeleted = :isDeleted ORDER BY c.createdAt DESC", countQuery = "SELECT COUNT(DISTINCT c) FROM Comment c WHERE c.userId = :userId AND c.isDeleted = :isDeleted")
        Page<Comment> findByUserAndIsDeletedOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("isDeleted") Boolean isDeleted, Pageable pageable);

        @Query(value = """
                        SELECT DISTINCT c
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        JOIN p.board b
                        WHERE c.userId = :userId
                          AND c.isDeleted = false
                          AND c.isBlinded = false
                          AND p.isDeleted = false
                          AND p.isBlinded = false
                          AND (:blockedUserIdsEmpty = true OR p.userId NOT IN (:blockedUserIds))
            """ + CommentVisibilityJpql.VIEWER_READABLE_POST + """
                        ORDER BY c.createdAt DESC, c.commentId DESC
                        """, countQuery = """
                        SELECT COUNT(DISTINCT c)
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        JOIN p.board b
                        WHERE c.userId = :userId
                          AND c.isDeleted = false
                          AND c.isBlinded = false
                          AND p.isDeleted = false
                          AND p.isBlinded = false
                          AND (:blockedUserIdsEmpty = true OR p.userId NOT IN (:blockedUserIds))
            """ + CommentVisibilityJpql.VIEWER_READABLE_POST + """
                        """)
        Page<Comment> findVisibleMyComments(
                        @org.springframework.data.repository.query.Param("userId") Long userId,
                        @org.springframework.data.repository.query.Param("viewerIsSuperAdmin") boolean viewerIsSuperAdmin,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds,
                        @org.springframework.data.repository.query.Param("inquiryBoardUrl") String inquiryBoardUrl,
                        @org.springframework.data.repository.query.Param("legacyInquiryUserAccessEnabled") boolean legacyInquiryUserAccessEnabled,
                        Pageable pageable);

        long countByPostIdAndIsDeleted(Long postId, Boolean isDeleted);
        long countByAgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                        Long agentId,
                        LocalDateTime start,
                        LocalDateTime end);
        default long countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                        Long agentId, LocalDateTime start, LocalDateTime end) {
                return countByAgentIdAndCreatedAtBetweenAndIsDeletedFalse(agentId, start, end);
        }
        @Query("""
                        SELECT COUNT(c)
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        JOIN p.board b
                        WHERE c.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND (b.isListed = true OR b.isListed IS NULL)
                          AND b.agentUseYn = true
                        """)
        long countPublicProfileCommentsByAgentId(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId);

        @Query("""
                        SELECT COALESCE(SUM(c.likeCount), 0)
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        JOIN p.board b
                        WHERE c.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND (b.isListed = true OR b.isListed IS NULL)
                          AND b.agentUseYn = true
                        """)
        long sumPublicProfileCommentLikesByAgentId(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId);

        @Query("""
                        SELECT c
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        JOIN p.board b
                        WHERE c.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND (b.isListed = true OR b.isListed IS NULL)
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
                        JOIN Post p ON p.postId = c.postId
                        JOIN p.board b
                        WHERE c.createdAt >= :start
                          AND c.createdAt < :end
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND (b.isListed = true OR b.isListed IS NULL)
                          AND LOWER(b.boardUrl) <> :inquiryBoardUrl
                        """)
        long countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        @org.springframework.data.repository.query.Param("start") LocalDateTime start,
                        @org.springframework.data.repository.query.Param("end") LocalDateTime end,
                        @org.springframework.data.repository.query.Param("inquiryBoardUrl") String inquiryBoardUrl);
        Optional<Comment> findByCommentIdAndPostIdAndIsDeletedFalse(Long commentId, Long postId);
        default Optional<Comment> findByCommentIdAndPost_PostIdAndIsDeletedFalse(Long commentId, Long postId) {
                return findByCommentIdAndPostIdAndIsDeletedFalse(commentId, postId);
        }

        @Query("SELECT c.postId FROM Comment c WHERE c.commentId = :commentId")
        Optional<Long> findPostIdByCommentId(@Param("commentId") Long commentId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM Comment c WHERE c.commentId = :commentId")
        Optional<Comment> findByIdWithRelationsForUpdate(
                        @org.springframework.data.repository.query.Param("commentId") Long commentId);

        long countByUserId(Long userId);
        long countByUserIdAndIsDeleted(Long userId, Boolean isDeleted);
        default long countByUserAndIsDeleted(UserIdRef user, Boolean isDeleted) {
                return countByUserIdAndIsDeleted(user.getUserId(), isDeleted);
        }

        @Query("""
                        SELECT c.userId AS userId, COUNT(c) AS commentCount
                        FROM Comment c
                        WHERE c.userId IN :userIds
                          AND c.isDeleted = false
                        GROUP BY c.userId
                        """)
        List<UserCommentCountProjection> countActiveByUserIds(
                        @org.springframework.data.repository.query.Param("userIds") Collection<Long> userIds);

        @Query("""
                        SELECT COUNT(c)
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        JOIN p.board b
                        WHERE c.userId = :userId
                          AND c.isDeleted = false
                          AND c.isBlinded = false
                          AND p.isDeleted = false
                          AND p.isBlinded = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND (b.isListed = true OR b.isListed IS NULL)
                        """)
        long countPublicProfileCommentsByUser(@org.springframework.data.repository.query.Param("userId") Long userId);
        default long countPublicProfileCommentsByUser(UserIdRef user) {
                return countPublicProfileCommentsByUser(user.getUserId());
        }

        @Query("""
                        SELECT c
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        JOIN p.board b
                        WHERE c.userId = :userId
                          AND c.isDeleted = false
                          AND c.isBlinded = false
                          AND p.isDeleted = false
                          AND p.isBlinded = false
                          AND p.isSecret = false
                          AND b.isActive = true
                          AND b.isPublic = true
                          AND (b.isListed = true OR b.isListed IS NULL)
                        """)
        Page<Comment> findPublicProfileCommentsByUser(
                        @org.springframework.data.repository.query.Param("userId") Long userId,
                        Pageable pageable);
        default Page<Comment> findPublicProfileCommentsByUser(UserIdRef user, Pageable pageable) {
                return findPublicProfileCommentsByUser(user.getUserId(), pageable);
        }
        boolean existsByPostIdAndAgentIdAndIsDeletedFalse(Long postId, Long agentId);
        @Query("""
                        SELECT DISTINCT c.postId
                        FROM Comment c
                        WHERE c.postId IN :postIds
                          AND c.agentId = :agentId
                          AND c.isDeleted = false
                        """)
        List<Long> findDistinctPostIdsByPostIdInAndAgentIdAndIsDeletedFalse(
                        @org.springframework.data.repository.query.Param("postIds") List<Long> postIds,
                        @org.springframework.data.repository.query.Param("agentId") Long agentId);
        default List<Long> findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(
                        List<Long> postIds, Long agentId) {
                return findDistinctPostIdsByPostIdInAndAgentIdAndIsDeletedFalse(postIds, agentId);
        }

        @Query("""
                        SELECT c
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        WHERE p.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND (c.agentId IS NULL OR c.agentId <> :agentId)
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
                                   WHERE c.postId = p.postId
                                     AND c.isDeleted = false
                                     AND (c.agentId IS NULL OR c.agentId <> :agentId)
                                     AND c.createdAt > COALESCE(read.lastReadAt, p.createdAt)
                               ) AS unreadCount
                        FROM Comment latest
                        JOIN Post p ON p.postId = latest.postId
                        JOIN p.board b
                        LEFT JOIN AgentPostActivityRead read
                          ON read.agent.agentId = :agentId
                         AND read.postId = p.postId
                        WHERE p.agentId = :agentId
                          AND latest.isDeleted = false
                          AND p.isDeleted = false
                          AND (latest.agentId IS NULL OR latest.agentId <> :agentId)
                          AND latest.createdAt > COALESCE(read.lastReadAt, p.createdAt)
                          AND NOT EXISTS (
                              SELECT 1
                              FROM Comment newer
                              WHERE newer.postId = p.postId
                                AND newer.isDeleted = false
                                AND (newer.agentId IS NULL OR newer.agentId <> :agentId)
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
                        JOIN Post p ON p.postId = c.postId
                        LEFT JOIN AgentPostActivityRead read
                          ON read.agent.agentId = :agentId
                         AND read.postId = p.postId
                        WHERE p.postId = :postId
                          AND p.agentId = :agentId
                          AND c.isDeleted = false
                          AND p.isDeleted = false
                          AND (c.agentId IS NULL OR c.agentId <> :agentId)
                          AND c.createdAt > COALESCE(read.lastReadAt, p.createdAt)
                        """)
        long countUnreadCommentsOnAgentPost(
                        @org.springframework.data.repository.query.Param("agentId") Long agentId,
                        @org.springframework.data.repository.query.Param("postId") Long postId);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"parent"})
        Page<Comment> findByUserIdOrderByCreatedAtDescCommentIdDesc(Long userId, Pageable pageable);
        default Page<Comment> findByUserOrderByCreatedAtDescCommentIdDesc(UserIdRef user, Pageable pageable) {
                return findByUserIdOrderByCreatedAtDescCommentIdDesc(user.getUserId(), pageable);
        }

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c FROM Comment c JOIN CommentClosure cc ON c.commentId = cc.id.descendantId WHERE cc.id.ancestorId IN :ancestorIds AND cc.depth > 0 AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT r FROM Comment r WHERE r.parent = c AND r.isDeleted = false))) ORDER BY c.createdAt ASC, c.commentId ASC")
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
                                             OR c.userId NOT IN (:blockedUserIds))
                                )
                                OR EXISTS (
                                        SELECT 1
                                        FROM CommentClosure cc
                                        JOIN cc.descendant descendant
                                        WHERE cc.ancestor = c
                                          AND cc.depth > 0
                                          AND descendant.isDeleted = false
                                          AND (:blockedUserIdsEmpty = true
                                               OR descendant.userId NOT IN (:blockedUserIds))
                                )
                          )
                        GROUP BY c.parent.commentId
                        """)
        List<ReplyCountProjection> countVisibleRepliesByParentIds(
                        @org.springframework.data.repository.query.Param("parentIds") Collection<Long> parentIds,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds);

        @Query("""
                        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
                        FROM Comment c
                        WHERE c.parent.commentId = :parentId
                          AND (
                                (
                                        c.isDeleted = false
                                        AND (:blockedUserIdsEmpty = true
                                             OR c.userId NOT IN (:blockedUserIds))
                                )
                                OR EXISTS (
                                        SELECT 1
                                        FROM CommentClosure cc
                                        JOIN cc.descendant descendant
                                        WHERE cc.ancestor = c
                                          AND cc.depth > 0
                                          AND descendant.isDeleted = false
                                          AND (:blockedUserIdsEmpty = true
                                               OR descendant.userId NOT IN (:blockedUserIds))
                                )
                          )
                        """)
        boolean existsVisibleReplyByParentId(
                        @org.springframework.data.repository.query.Param("parentId") Long parentId,
                        @org.springframework.data.repository.query.Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                        @org.springframework.data.repository.query.Param("blockedUserIds") Collection<Long> blockedUserIds);

        @Query("""
                        SELECT DISTINCT c.postId
                        FROM Comment c
                        JOIN Post p ON p.postId = c.postId
                        WHERE c.postId IN :postIds
                          AND c.isDeleted = false
                          AND c.userId <> p.userId
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
