package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board_subscriptions")
@IdClass(BoardSubscriptionId.class)
public class BoardSubscription extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // MEMBER, MODERATOR, BANNED

    @Builder
    public BoardSubscription(User user, Board board, String role) {
        this.user = user;
        this.board = board;
        this.role = role;
    }
}
