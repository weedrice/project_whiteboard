package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {
    Page<Comment> findByPost_PostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(Long postId, String isDeleted, Pageable pageable);
    Page<Comment> findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(Long parentId, String isDeleted, Pageable pageable);
    List<Comment> findByParent_CommentIdInAndIsDeletedOrderByCreatedAtAsc(List<Long> parentIds, String isDeleted);
    Page<Comment> findByUserAndIsDeletedOrderByCreatedAtDesc(User user, String isDeleted, Pageable pageable);
    long countByPost_PostIdAndIsDeleted(Long postId, String isDeleted);
    long countByUser(User user);
    Page<Comment> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}
