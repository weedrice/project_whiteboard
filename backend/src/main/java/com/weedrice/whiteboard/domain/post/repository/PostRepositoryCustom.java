package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Post;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepositoryCustom {
    Page<Post> findByBoardIdAndCategoryId(Long boardId, Long categoryId, Integer minLikes, List<Long> blockedUserIds, @NonNull Pageable pageable);

    Page<Post> searchPostsByKeyword(String keyword, List<Long> blockedUserIds, @NonNull Pageable pageable);

    Page<Post> searchPosts(String keyword, String searchType, String boardUrl, List<Long> blockedUserIds, @NonNull Pageable pageable);

    Page<Post> findByTagId(Long tagId, List<Long> blockedUserIds, @NonNull Pageable pageable);

    List<Post> findNoticesByBoardId(Long boardId, Boolean isNotice, Boolean isDeleted, List<Long> blockedUserIds);

    List<Post> findLatestPostsByBoardId(Long boardId, Boolean isDeleted, List<Long> blockedUserIds, Pageable pageable);

    List<Post> findTrendingPosts(LocalDateTime since, List<Long> blockedUserIds, Pageable pageable);
}
