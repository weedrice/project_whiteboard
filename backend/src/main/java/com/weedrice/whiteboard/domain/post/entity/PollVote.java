package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "poll_votes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PollVote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long voteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private PollOption option;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder
    public PollVote(Poll poll, PollOption option, Long userId) {
        this.poll = poll;
        this.option = option;
        this.userId = userId;
    }

    public static class PollVoteBuilder {
        public PollVoteBuilder user(UserIdRef user) {
            this.userId = user == null ? null : user.getUserId();
            return this;
        }
    }
}
