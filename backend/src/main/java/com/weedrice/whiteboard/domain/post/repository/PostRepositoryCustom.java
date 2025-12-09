package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

public interface PostRepositoryCustom {
    Page<Post> findByBoardIdAndCategoryId(Long boardId, Long categoryId, Integer minLikes, @NonNull Pageable pageable);

    Page<Post> searchPostsByKeyword(String keyword, @NonNull Pageable pageable);

    Page<Post> searchPosts(String keyword, String searchType, String boardUrl, @NonNull Pageable pageable);

    Page<Post> findByTagId(Long tagId, @NonNull Pageable pageable);
}
