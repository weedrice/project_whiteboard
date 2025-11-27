package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostRepositoryCustom {
    Page<Post> findByBoardIdAndCategoryId(Long boardId, Long categoryId, Integer minLikes, Pageable pageable);
    Page<Post> searchPostsByKeyword(String keyword, Pageable pageable);
    Page<Post> findByTagId(Long tagId, Pageable pageable);
}
