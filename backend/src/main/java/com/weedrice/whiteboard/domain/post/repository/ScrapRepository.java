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
    @EntityGraph(attributePaths = { "post", "post.board", "post.user", "post.agent" })
    @Query(value = """
            SELECT s
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.user = :user
              AND p.isDeleted = false
              AND (:folderId IS NULL OR s.folder.folderId = :folderId)
              AND (:blockedUserIdsEmpty = true OR p.user.userId NOT IN (:blockedUserIds))
            """ + PostVisibilityJpql.VIEWER_READABLE_POST + """
            ORDER BY s.createdAt DESC, p.postId DESC
            """, countQuery = """
            SELECT COUNT(s)
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.user = :user
              AND p.isDeleted = false
              AND (:folderId IS NULL OR s.folder.folderId = :folderId)
              AND (:blockedUserIdsEmpty = true OR p.user.userId NOT IN (:blockedUserIds))
            """ + PostVisibilityJpql.VIEWER_READABLE_POST + """
            """)
    Page<Scrap> findPageByUserWithPostDetails(
            @Param("user") User user,
            @Param("folderId") Long folderId,
            @Param("viewerIsSuperAdmin") boolean viewerIsSuperAdmin,
            @Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
            @Param("blockedUserIds") Collection<Long> blockedUserIds,
            @Param("inquiryBoardUrl") String inquiryBoardUrl,
            Pageable pageable);

    @EntityGraph(attributePaths = { "post", "post.board", "post.user", "post.agent" })
    @Query(value = """
            SELECT s
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.user = :user
              AND p.isDeleted = false
              AND (:folderId IS NULL OR s.folder.folderId = :folderId)
              AND (LOWER(p.title) LIKE :keywordPattern ESCAPE '!'
                   OR LOWER(COALESCE(s.remark, '')) LIKE :keywordPattern ESCAPE '!')
              AND (:blockedUserIdsEmpty = true OR p.user.userId NOT IN (:blockedUserIds))
            """ + PostVisibilityJpql.VIEWER_READABLE_POST + """
            ORDER BY s.createdAt DESC, p.postId DESC
            """, countQuery = """
            SELECT COUNT(s)
            FROM Scrap s
            JOIN s.post p
            JOIN p.board b
            WHERE s.user = :user
              AND p.isDeleted = false
              AND (:folderId IS NULL OR s.folder.folderId = :folderId)
              AND (LOWER(p.title) LIKE :keywordPattern ESCAPE '!'
                   OR LOWER(COALESCE(s.remark, '')) LIKE :keywordPattern ESCAPE '!')
              AND (:blockedUserIdsEmpty = true OR p.user.userId NOT IN (:blockedUserIds))
            """ + PostVisibilityJpql.VIEWER_READABLE_POST + """
            """)
    Page<Scrap> findPageByUserWithPostDetailsByKeyword(
            @Param("user") User user,
            @Param("folderId") Long folderId,
            @Param("keywordPattern") String keywordPattern,
            @Param("viewerIsSuperAdmin") boolean viewerIsSuperAdmin,
            @Param("blockedUserIdsEmpty") boolean blockedUserIdsEmpty,
            @Param("blockedUserIds") Collection<Long> blockedUserIds,
            @Param("inquiryBoardUrl") String inquiryBoardUrl,
            Pageable pageable);

    default Page<Scrap> findPageByUserWithPostDetails(
            User user,
            boolean viewerIsSuperAdmin,
            boolean blockedUserIdsEmpty,
            Collection<Long> blockedUserIds,
            String inquiryBoardUrl,
            Pageable pageable) {
        return findPageByUserWithPostDetails(user, null, viewerIsSuperAdmin, blockedUserIdsEmpty,
                blockedUserIds, inquiryBoardUrl, pageable);
    }

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
