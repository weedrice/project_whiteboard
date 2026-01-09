package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {
        @org.springframework.data.jpa.repository.Query(value = "SELECT DISTINCT c FROM Comment c JOIN FETCH c.user JOIN FETCH c.post p JOIN FETCH p.board WHERE c.post.postId = :postId AND c.parent IS NULL AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT r FROM Comment r WHERE r.parent = c AND r.isDeleted = false))) ORDER BY c.createdAt ASC", countQuery = "SELECT COUNT(DISTINCT c) FROM Comment c WHERE c.post.postId = :postId AND c.parent IS NULL AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT r FROM Comment r WHERE r.parent = c AND r.isDeleted = false)))")
        Page<Comment> findParentsWithChildrenOrNotDeleted(
                        @org.springframework.data.repository.query.Param("postId") Long postId, Pageable pageable);

        Page<Comment> findByPost_PostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(Long postId, Boolean isDeleted,
                        Pageable pageable);

        Page<Comment> findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(Long parentId, Boolean isDeleted,
                        Pageable pageable);

        List<Comment> findByParent_CommentIdInAndIsDeletedOrderByCreatedAtAsc(List<Long> parentIds, Boolean isDeleted);

        @org.springframework.data.jpa.repository.Query(value = "SELECT DISTINCT c FROM Comment c JOIN FETCH c.post p JOIN FETCH p.board WHERE c.user = :user AND c.isDeleted = :isDeleted ORDER BY c.createdAt DESC", countQuery = "SELECT COUNT(DISTINCT c) FROM Comment c WHERE c.user = :user AND c.isDeleted = :isDeleted")
        Page<Comment> findByUserAndIsDeletedOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("user") User user, @org.springframework.data.repository.query.Param("isDeleted") Boolean isDeleted, Pageable pageable);

        long countByPost_PostIdAndIsDeleted(Long postId, Boolean isDeleted);

        long countByUser(User user);

        Page<Comment> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

        Page<Comment> findByContentContainingIgnoreCaseAndIsDeleted(String content, Boolean isDeleted,
                        Pageable pageable); // Added for IntegratedSearch

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c FROM Comment c JOIN FETCH c.user JOIN FETCH c.post p JOIN FETCH p.board JOIN CommentClosure cc ON c.commentId = cc.id.descendantId WHERE cc.id.ancestorId IN :ancestorIds AND cc.depth > 0 AND (c.isDeleted = false OR (c.isDeleted = true AND EXISTS (SELECT r FROM Comment r WHERE r.parent = c AND r.isDeleted = false))) ORDER BY c.createdAt ASC")
        List<Comment> findAllDescendants(
                        @org.springframework.data.repository.query.Param("ancestorIds") List<Long> ancestorIds);
}
