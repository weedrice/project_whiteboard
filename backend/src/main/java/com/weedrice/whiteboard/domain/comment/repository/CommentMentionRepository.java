package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.CommentMention;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {
    @EntityGraph(attributePaths = {"comment", "user"})
    List<CommentMention> findByCommentCommentIdIn(Collection<Long> commentIds);

    void deleteByCommentCommentId(Long commentId);
}
