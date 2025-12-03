package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.comment.entity.CommentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentVersionRepository extends JpaRepository<CommentVersion, Long> {
    List<CommentVersion> findByComment_CommentIdOrderByCreatedAtDesc(Long commentId);
}
