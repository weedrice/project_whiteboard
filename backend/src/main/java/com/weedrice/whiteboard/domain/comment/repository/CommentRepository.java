package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {
    @org.springframework.data.jpa.repository.Query(value = "SELECT c FROM Comment c WHERE c.post.postId = :postId AND c.parent IS NULL AND (c.isDeleted = 'N' OR (c.isDeleted = 'Y' AND EXISTS (SELECT r FROM Comment r WHERE r.parent = c AND r.isDeleted = 'N'))) ORDER BY c.createdAt ASC", countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.post.postId = :postId AND c.parent IS NULL AND (c.isDeleted = 'N' OR (c.isDeleted = 'Y' AND EXISTS (SELECT r FROM Comment r WHERE r.parent = c AND r.isDeleted = 'N')))")
    Page<Comment> findParentsWithChildrenOrNotDeleted(
            @org.springframework.data.repository.query.Param("postId") Long postId, Pageable pageable);

    Page<Comment> findByPost_PostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(Long postId, String isDeleted,
            Pageable pageable);

    Page<Comment> findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(Long parentId, String isDeleted,
            Pageable pageable);

    List<Comment> findByParent_CommentIdInAndIsDeletedOrderByCreatedAtAsc(List<Long> parentIds, String isDeleted);

    Page<Comment> findByUserAndIsDeletedOrderByCreatedAtDesc(User user, String isDeleted, Pageable pageable);

    long countByPost_PostIdAndIsDeleted(Long postId, String isDeleted);

    long countByUser(User user);

    Page<Comment> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<Comment> findByContentContainingIgnoreCaseAndIsDeleted(String content, String isDeleted, Pageable pageable); // Added for IntegratedSearch
}
