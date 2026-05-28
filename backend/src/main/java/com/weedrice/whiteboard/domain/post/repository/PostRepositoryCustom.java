package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PostRepositoryCustom {
    Page<Post> findByBoardIdAndCategoryId(Long boardId, Long categoryId, String keyword, Integer minLikes, List<Long> blockedUserIds,
            Boolean includeSecret, Long viewerUserId, @NonNull Pageable pageable);

    Page<PostListSummaryProjection> findPostListSummariesByBoardIdAndCategoryId(
            Long boardId,
            Long categoryId,
            String keyword,
            Integer minLikes,
            List<Long> blockedUserIds,
            Boolean includeSecret,
            Long viewerUserId,
            @NonNull Pageable pageable);

    long countPostsBeforeInBoardDefaultOrder(Long boardId, LocalDateTime createdAt, Long postId,
            List<Long> blockedUserIds, Boolean includeSecret, Long viewerUserId);

    Page<Post> searchPostsByKeyword(String keyword, List<Long> blockedUserIds, Long viewerUserId,
            @NonNull Pageable pageable);

    Page<Post> searchPosts(String keyword, String searchType, String boardUrl, List<Long> blockedUserIds,
            Boolean includeSecret, Long viewerUserId, @NonNull Pageable pageable);

    Page<Post> findByTagId(Long tagId, List<Long> blockedUserIds, @NonNull Pageable pageable);

    List<Post> findNoticesByBoardId(Long boardId, Boolean isNotice, Boolean isDeleted, List<Long> blockedUserIds,
            Boolean includeSecret, Long viewerUserId);

    List<Post> findLatestPostsByBoardId(Long boardId, Boolean isDeleted, List<Long> blockedUserIds,
            Boolean includeSecret, Long viewerUserId, Pageable pageable);

    List<Post> findTrendingPosts(LocalDateTime since, List<Long> blockedUserIds, Pageable pageable);

    List<Post> findTrendingPosts(LocalDateTime since, List<Long> blockedUserIds, long offset, int limit);

    long countTrendingPosts(LocalDateTime since, List<Long> blockedUserIds);

    List<Post> findPublicLandingLatestPosts(String inquiryBoardUrl, List<Long> blockedUserIds, Pageable pageable);

    Page<Post> findAgentFeedByBoardIds(Collection<Long> boardIds, List<Long> blockedUserIds,
            Collection<Long> secretVisibleBoardIds, Long viewerUserId, @NonNull Pageable pageable);

    List<Long> findLatestPostIdsByBoardIds(Collection<Long> boardIds, int limitPerBoard, List<Long> blockedUserIds,
            Collection<Long> secretVisibleBoardIds, Long viewerUserId);

    /**
     * Post를 ID로 조회하면서 연관된 User, Board, Category를 함께 fetch join합니다.
     * N+1 쿼리 문제를 방지하기 위해 사용합니다.
     */
    java.util.Optional<Post> findByIdWithRelations(@NonNull Long postId);
}
