package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLikeId> {
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM CommentLike cl WHERE cl.id.user = :userId AND cl.id.comment = :commentId")
    int deleteByUserIdAndCommentId(Long userId, Long commentId);
}
