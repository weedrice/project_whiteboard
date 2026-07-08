package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board_visits")
@IdClass(BoardVisitId.class)
public class BoardVisit extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @Column(name = "last_visited_at", nullable = false)
    private LocalDateTime lastVisitedAt;

    public BoardVisit(User user, Board board, LocalDateTime lastVisitedAt) {
        this.user = user;
        this.board = board;
        this.lastVisitedAt = lastVisitedAt;
    }

    public void touch(LocalDateTime visitedAt) {
        this.lastVisitedAt = visitedAt;
    }
}
