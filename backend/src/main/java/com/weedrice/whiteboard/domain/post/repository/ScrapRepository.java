package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.post.entity.Post;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ScrapRepository extends JpaRepository<Scrap, ScrapId> {
    @EntityGraph(attributePaths = { "post", "post.board" })
    @Query(value = """
            SELECT s
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.userId = :userId
              AND p.isDeleted = false
              AND (:folderId IS NULL OR s.folder.folderId = :folderId)
              AND (:blockedUserIdsEmpty = true OR p.userId NOT IN (:blockedUserIds))
            """ + PostVisibilityJpql.VIEWER_READABLE_POST + """
            ORDER BY s.createdAt DESC, p.postId DESC
            """, countQuery = """
            SELECT COUNT(s)
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.userId = :userId
              AND p.isDeleted = false
              AND (:folderId IS NULL OR s.folder.folderId = :folderId)
              AND (:blockedUserIdsEmpty = true OR p.userId NOT IN (:blockedUserIds))
            """ + PostVisibilityJpql.VIEWER_READABLE_POST + """
            """)
    Page<Scrap> findPageByUserWithPostDetails(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId,
            @Param("viewerIsSuperAdmin") boolean viewerIsSuperAdmin,
            @Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
            @Param("blockedUserIds") Collection<Long> blockedUserIds,
            @Param("inquiryBoardUrl") String inquiryBoardUrl,
            @Param("legacyInquiryUserAccessEnabled") boolean legacyInquiryUserAccessEnabled,
            Pageable pageable);
    default Page<Scrap> findPageByUserWithPostDetails(
            UserIdRef user, Long folderId, boolean viewerIsSuperAdmin, boolean blockedUserIdsEmpty,
            Collection<Long> blockedUserIds, String inquiryBoardUrl,
            boolean legacyInquiryUserAccessEnabled, Pageable pageable) {
        return findPageByUserWithPostDetails(user.getUserId(), folderId, viewerIsSuperAdmin,
                blockedUserIdsEmpty, blockedUserIds, inquiryBoardUrl, legacyInquiryUserAccessEnabled, pageable);
    }

    @EntityGraph(attributePaths = { "post", "post.board" })
    @Query(value = """
            SELECT s
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.userId = :userId
              AND p.isDeleted = false
              AND (:folderId IS NULL OR s.folder.folderId = :folderId)
              AND (LOWER(p.title) LIKE :keywordPattern ESCAPE '!'
                   OR LOWER(COALESCE(s.remark, '')) LIKE :keywordPattern ESCAPE '!')
              AND (:blockedUserIdsEmpty = true OR p.userId NOT IN (:blockedUserIds))
            """ + PostVisibilityJpql.VIEWER_READABLE_POST + """
            ORDER BY s.createdAt DESC, p.postId DESC
            """, countQuery = """
            SELECT COUNT(s)
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.userId = :userId
              AND p.isDeleted = false
              AND (:folderId IS NULL OR s.folder.folderId = :folderId)
              AND (LOWER(p.title) LIKE :keywordPattern ESCAPE '!'
                   OR LOWER(COALESCE(s.remark, '')) LIKE :keywordPattern ESCAPE '!')
              AND (:blockedUserIdsEmpty = true OR p.userId NOT IN (:blockedUserIds))
            """ + PostVisibilityJpql.VIEWER_READABLE_POST + """
            """)
    Page<Scrap> findPageByUserWithPostDetailsByKeyword(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId,
            @Param("keywordPattern") String keywordPattern,
            @Param("viewerIsSuperAdmin") boolean viewerIsSuperAdmin,
            @Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
            @Param("blockedUserIds") Collection<Long> blockedUserIds,
            @Param("inquiryBoardUrl") String inquiryBoardUrl,
            @Param("legacyInquiryUserAccessEnabled") boolean legacyInquiryUserAccessEnabled,
            Pageable pageable);
    default Page<Scrap> findPageByUserWithPostDetailsByKeyword(
            UserIdRef user, Long folderId, String keywordPattern, boolean viewerIsSuperAdmin,
            boolean blockedUserIdsEmpty, Collection<Long> blockedUserIds, String inquiryBoardUrl,
            boolean legacyInquiryUserAccessEnabled, Pageable pageable) {
        return findPageByUserWithPostDetailsByKeyword(user.getUserId(), folderId, keywordPattern,
                viewerIsSuperAdmin, blockedUserIdsEmpty, blockedUserIds, inquiryBoardUrl,
                legacyInquiryUserAccessEnabled, pageable);
    }

    default Page<Scrap> findPageByUserWithPostDetails(
            Long userId,
            boolean viewerIsSuperAdmin,
            boolean blockedUserIdsEmpty,
            Collection<Long> blockedUserIds,
            String inquiryBoardUrl,
            boolean legacyInquiryUserAccessEnabled,
            Pageable pageable) {
        return findPageByUserWithPostDetails(userId, null, viewerIsSuperAdmin, blockedUserIdsEmpty,
                blockedUserIds, inquiryBoardUrl, legacyInquiryUserAccessEnabled, pageable);
    }

    default Page<Scrap> findPageByUserWithPostDetails(
            UserIdRef user,
            boolean viewerIsSuperAdmin,
            boolean blockedUserIdsEmpty,
            Collection<Long> blockedUserIds,
            String inquiryBoardUrl,
            boolean legacyInquiryUserAccessEnabled,
            Pageable pageable) {
        return findPageByUserWithPostDetails(user.getUserId(), null, viewerIsSuperAdmin, blockedUserIdsEmpty,
                blockedUserIds, inquiryBoardUrl, legacyInquiryUserAccessEnabled, pageable);
    }

    List<Scrap> findByUserIdAndPostIn(Long userId, List<Post> posts);

    @Query("""
            SELECT s.post.postId
            FROM Scrap s
            WHERE s.userId = :userId
              AND s.post.postId IN :postIds
            """)
    List<Long> findPostIdsByUserIdAndPostIdIn(@Param("userId") Long userId,
            @Param("postIds") Collection<Long> postIds);

    long deleteByUserIdAndPost_PostId(Long userId, Long postId);
    default long deleteByUser_UserIdAndPost_PostId(Long userId, Long postId) {
        return deleteByUserIdAndPost_PostId(userId, postId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Scrap s WHERE s.userId = :userId AND s.post.postId = :postId")
    Optional<Scrap> findOwnedByPostIdForUpdate(
            @Param("userId") Long userId,
            @Param("postId") Long postId);
}
