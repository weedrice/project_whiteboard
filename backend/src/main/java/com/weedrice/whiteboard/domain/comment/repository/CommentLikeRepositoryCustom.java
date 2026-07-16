package com.weedrice.whiteboard.domain.comment.repository;

public interface CommentLikeRepositoryCustom {

    int insertIgnore(Long userId, Long commentId);
}
