package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScrapRepository extends JpaRepository<Scrap, ScrapId> {
    @EntityGraph(attributePaths = { "post", "post.board", "post.user" })
    @Query(value = """
            SELECT s
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.user = :user
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
            ORDER BY s.createdAt DESC, p.postId DESC
            """, countQuery = """
            SELECT COUNT(s)
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.user = :user
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
    Page<Scrap> findPageByUserWithPostDetails(
            @Param("user") User user,
            @Param("viewerIsSuperAdmin") boolean viewerIsSuperAdmin,
            @Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
            @Param("blockedUserIds") Collection<Long> blockedUserIds,
            @Param("inquiryBoardUrl") String inquiryBoardUrl,
            Pageable pageable);

    List<Scrap> findByUserAndPostIn(User user, List<Post> posts);

    @Query("""
            SELECT s.post.postId
            FROM Scrap s
            WHERE s.user.userId = :userId
              AND s.post.postId IN :postIds
            """)
    List<Long> findPostIdsByUserIdAndPostIdIn(@Param("userId") Long userId,
            @Param("postIds") Collection<Long> postIds);

    long deleteByUser_UserIdAndPost_PostId(Long userId, Long postId);
}
