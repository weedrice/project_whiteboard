package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    interface OptionVoteCountProjection {
        Long getOptionId();

        Long getVoteCount();
    }

    List<PollVote> findByPoll_PollIdAndUser_UserId(Long pollId, Long userId);

    @Modifying
    void deleteByPoll_PollIdAndUser_UserId(Long pollId, Long userId);

    @Query("""
            SELECT vote.option.optionId AS optionId, COUNT(vote) AS voteCount
            FROM PollVote vote
            WHERE vote.poll.pollId = :pollId
            GROUP BY vote.option.optionId
            """)
    List<OptionVoteCountProjection> countByOption(@Param("pollId") Long pollId);

    @Query("""
            SELECT vote.option.optionId
            FROM PollVote vote
            WHERE vote.poll.pollId = :pollId
              AND vote.user.userId = :userId
            """)
    List<Long> findSelectedOptionIds(@Param("pollId") Long pollId, @Param("userId") Long userId);

    boolean existsByPoll_PollIdAndOption_OptionIdIn(Long pollId, Collection<Long> optionIds);
}
