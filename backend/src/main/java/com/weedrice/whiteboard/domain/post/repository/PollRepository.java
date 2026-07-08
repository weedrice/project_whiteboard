package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Poll;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {
    @EntityGraph(attributePaths = {"options", "post"})
    Optional<Poll> findByPost_PostId(Long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"options", "post"})
    @Query("SELECT poll FROM Poll poll WHERE poll.post.postId = :postId")
    Optional<Poll> findByPostIdForUpdate(@Param("postId") Long postId);

    void deleteByPost_PostId(Long postId);
}
