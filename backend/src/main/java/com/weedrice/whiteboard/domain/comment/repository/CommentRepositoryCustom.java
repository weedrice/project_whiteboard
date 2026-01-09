package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentRepositoryCustom {
    Page<Comment> searchCommentsByKeyword(String keyword, Pageable pageable);

    /**
     * Comment를 ID로 조회하면서 연관된 User, Post, Board를 함께 fetch join합니다.
     * N+1 쿼리 문제를 방지하기 위해 사용합니다.
     */
    java.util.Optional<Comment> findByIdWithRelations(@org.springframework.lang.NonNull Long commentId);
}
