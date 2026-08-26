package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface ViewHistoryRepository extends JpaRepository<ViewHistory, Long> {
    @EntityGraph(attributePaths = "lastReadComment")
    Optional<ViewHistory> findByUserAndPost(User user, Post post);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT vh
            FROM ViewHistory vh
            WHERE vh.user.userId = :userId
              AND vh.post.postId = :postId
            """)
    Optional<ViewHistory> findByUserAndPostForUpdate(@Param("userId") Long userId, @Param("postId") Long postId);

    @Modifying
    @Query(value = """
            INSERT INTO view_histories (
                user_id,
                post_id,
                duration_ms,
                created_at,
                modified_at
            )
            VALUES (
                :userId,
                :postId,
                0,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (user_id, post_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("postId") Long postId);

    @Modifying
    @Query(value = """
            UPDATE view_histories
            SET modified_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND post_id = :postId
            """, nativeQuery = true)
    int touchModifiedAt(@Param("userId") Long userId, @Param("postId") Long postId);

    @Query(value = """
            SELECT vh.post_id
            FROM view_histories vh
            JOIN posts p ON p.post_id = vh.post_id
            JOIN boards b ON b.board_id = p.board_id
            WHERE vh.user_id = :userId
              AND p.is_deleted = 'N'
              AND p.is_blinded = 'N'
              AND (:blockedUserIdsEmpty = true OR p.user_id NOT IN (:blockedUserIds))
              AND (
                    LOWER(b.board_url) <> :inquiryBoardUrl
                    OR :legacyInquiryUserAccessEnabled = true
                    OR :isSuperAdmin = true
                  )
              AND (
                    b.is_active = 'Y'
                    OR :isSuperAdmin = true
                    OR p.user_id = :userId
                    OR EXISTS (
                        SELECT 1
                        FROM admins a
                        WHERE a.board_id = b.board_id
                          AND a.user_id = :userId
                          AND a.is_active = 'Y'
                    )
                  )
              AND (
                    b.is_public = 'Y'
                    OR :isSuperAdmin = true
                    OR EXISTS (
                        SELECT 1
                        FROM admins a
                        WHERE a.board_id = b.board_id
                          AND a.user_id = :userId
                          AND a.is_active = 'Y'
                    )
                    OR (LOWER(b.board_url) = :inquiryBoardUrl AND p.user_id = :userId)
                  )
              AND (
                    p.is_secret = 'N'
                    OR :isSuperAdmin = true
                    OR p.user_id = :userId
                    OR EXISTS (
                        SELECT 1
                        FROM admins a
                        WHERE a.board_id = b.board_id
                          AND a.user_id = :userId
                          AND a.is_active = 'Y'
                    )
                  )
            ORDER BY vh.modified_at DESC, vh.post_id DESC
            """, countQuery = """
            SELECT COUNT(*)
            FROM view_histories vh
            JOIN posts p ON p.post_id = vh.post_id
            JOIN boards b ON b.board_id = p.board_id
            WHERE vh.user_id = :userId
              AND p.is_deleted = 'N'
              AND p.is_blinded = 'N'
              AND (:blockedUserIdsEmpty = true OR p.user_id NOT IN (:blockedUserIds))
              AND (
                    LOWER(b.board_url) <> :inquiryBoardUrl
                    OR :legacyInquiryUserAccessEnabled = true
                    OR :isSuperAdmin = true
                  )
              AND (
                    b.is_active = 'Y'
                    OR :isSuperAdmin = true
                    OR p.user_id = :userId
                    OR EXISTS (
                        SELECT 1
                        FROM admins a
                        WHERE a.board_id = b.board_id
                          AND a.user_id = :userId
                          AND a.is_active = 'Y'
                    )
                  )
              AND (
                    b.is_public = 'Y'
                    OR :isSuperAdmin = true
                    OR EXISTS (
                        SELECT 1
                        FROM admins a
                        WHERE a.board_id = b.board_id
                          AND a.user_id = :userId
                          AND a.is_active = 'Y'
                    )
                    OR (LOWER(b.board_url) = :inquiryBoardUrl AND p.user_id = :userId)
                  )
              AND (
                    p.is_secret = 'N'
                    OR :isSuperAdmin = true
                    OR p.user_id = :userId
                    OR EXISTS (
                        SELECT 1
                        FROM admins a
                        WHERE a.board_id = b.board_id
                          AND a.user_id = :userId
                          AND a.is_active = 'Y'
                    )
                  )
            """,
            nativeQuery = true)
    Page<Long> findVisiblePostIdsByUserIdOrderByModifiedAtDesc(@Param("userId") Long userId,
                                                               @Param("isSuperAdmin") boolean isSuperAdmin,
                                                               @Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
                                                               @Param("blockedUserIds") Collection<Long> blockedUserIds,
                                                               @Param("inquiryBoardUrl") String inquiryBoardUrl,
                                                               @Param("legacyInquiryUserAccessEnabled") boolean legacyInquiryUserAccessEnabled,
                                                               Pageable pageable);

}
