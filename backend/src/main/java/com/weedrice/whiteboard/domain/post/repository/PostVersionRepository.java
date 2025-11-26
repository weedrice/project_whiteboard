package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostVersionRepository extends JpaRepository<PostVersion, Long> {
    List<PostVersion> findByPost_PostIdOrderByCreatedAtDesc(Long postId);
}
