package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.CommentClosure;
import com.weedrice.whiteboard.domain.comment.entity.CommentClosureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentClosureRepository extends JpaRepository<CommentClosure, CommentClosureId> {

    @Modifying
    @Query(value = "INSERT INTO comment_closures (ancestor_id, descendant_id, depth, created_at, modified_at) " +
            "SELECT ancestor_id, :descendantId, depth + 1, NOW(), NOW() " +
            "FROM comment_closures " +
            "WHERE descendant_id = :parentId " +
            "UNION ALL " +
            "SELECT :descendantId, :descendantId, 0, NOW(), NOW()", nativeQuery = true)
    void createClosures(@Param("descendantId") Long descendantId, @Param("parentId") Long parentId);

    @Modifying
    @Query(value = "INSERT INTO comment_closures (ancestor_id, descendant_id, depth, created_at, modified_at) " +
            "VALUES (:commentId, :commentId, 0, NOW(), NOW())", nativeQuery = true)
    void createSelfClosure(@Param("commentId") Long commentId);
}
