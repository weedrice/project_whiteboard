package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {
        interface ReplyCountProjection {
                Long getParentId();

                long getReplyCount();
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
                                )
                          )
                        ORDER BY c.createdAt ASC
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
                                )
                          )
                        """)
        Page<Comment> findParentsWithChildrenOrNotDeleted(
                        @org.springframework.data.repository.query.Param("postId") Long postId, Pageable pageable);

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
                                                )
                                        )
                                )
                          )
                        ORDER BY c.createdAt ASC
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
                                                )
                                        )
                                )
                          )
                        """)
        Page<Comment> findRepliesWithRelations(@org.springframework.data.repository.query.Param("parentId") Long parentId,
                        @org.springframework.data.repository.query.Param("isDeleted") Boolean isDeleted,
                        Pageable pageable);

        List<Comment> findByParent_CommentIdInAndIsDeletedOrderByCreatedAtAsc(List<Long> parentIds, Boolean isDeleted);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
        List<Comment> findByCommentIdIn(Collection<Long> commentIds);

        @org.springframework.data.jpa.repository.Query(value = "SELECT DISTINCT c FROM Comment c JOIN FETCH c.post p JOIN FETCH p.board WHERE c.user = :user AND c.isDeleted = :isDeleted ORDER BY c.createdAt DESC", countQuery = "SELECT COUNT(DISTINCT c) FROM Comment c WHERE c.user = :user AND c.isDeleted = :isDeleted")
        Page<Comment> findByUserAndIsDeletedOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("user") User user, @org.springframework.data.repository.query.Param("isDeleted") Boolean isDeleted, Pageable pageable);

        long countByPost_PostIdAndIsDeleted(Long postId, Boolean isDeleted);
        long countByAgent_AgentIdAndCreatedAtBetween(Long agentId, LocalDateTime start, LocalDateTime end);
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
                          AND LOWER(b.boardUrl) <> 'inquiry'
                        """)
        long countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        @org.springframework.data.repository.query.Param("start") LocalDateTime start,
                        @org.springframework.data.repository.query.Param("end") LocalDateTime end);
        Optional<Comment> findByCommentIdAndPost_PostId(Long commentId, Long postId);

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

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"agent", "parent", "post", "post.board"})
        Page<Comment> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c FROM Comment c JOIN FETCH c.user LEFT JOIN FETCH c.agent JOIN FETCH c.post p JOIN FETCH p.board JOIN CommentClosure cc ON c.commentId = cc.id.descendantId WHERE cc.id.ancestorId IN :ancestorIds AND cc.depth > 0 AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT r FROM Comment r WHERE r.parent = c AND r.isDeleted = false))) ORDER BY c.createdAt ASC")
        List<Comment> findAllDescendants(
                        @org.springframework.data.repository.query.Param("ancestorIds") List<Long> ancestorIds);

        @Query("""
                        SELECT c.parent.commentId AS parentId, COUNT(c) AS replyCount
                        FROM Comment c
                        WHERE c.parent.commentId IN :parentIds
                          AND (
                                c.isDeleted = false
                                OR EXISTS (
                                        SELECT 1
                                        FROM CommentClosure cc
                                        JOIN cc.descendant descendant
                                        WHERE cc.ancestor = c
                                          AND cc.depth > 0
                                          AND descendant.isDeleted = false
                                )
                          )
                        GROUP BY c.parent.commentId
                        """)
        List<ReplyCountProjection> countVisibleRepliesByParentIds(
                        @org.springframework.data.repository.query.Param("parentIds") Collection<Long> parentIds);

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
        @Query("UPDATE Comment c SET c.likeCount = c.likeCount + 1 WHERE c.commentId = :commentId")
        int incrementLikeCount(Long commentId);

        @Modifying(flushAutomatically = true)
        @Query("""
                UPDATE Comment c
                SET c.likeCount = CASE WHEN c.likeCount > 0 THEN c.likeCount - 1 ELSE 0 END
                WHERE c.commentId = :commentId
                """)
        int decrementLikeCount(Long commentId);
}
