package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPost_PostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(Long postId, String isDeleted, Pageable pageable);
    Page<Comment> findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(Long parentId, String isDeleted, Pageable pageable);
    long countByPost_PostIdAndIsDeleted(Long postId, String isDeleted);
}
