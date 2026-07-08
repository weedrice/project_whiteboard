package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Poll;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {
    @EntityGraph(attributePaths = {"options", "post"})
    Optional<Poll> findByPost_PostId(Long postId);

    void deleteByPost_PostId(Long postId);
}
