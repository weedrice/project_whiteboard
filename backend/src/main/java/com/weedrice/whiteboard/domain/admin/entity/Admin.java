package com.weedrice.whiteboard.domain.admin.entity;

import com.weedrice.whiteboard.domain.board.entity.Board;
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
@Table(name = "admins")
public class Admin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board; // NULL이면 전체 관리자

    @Column(name = "role", length = 50, nullable = false)
    private String role; // SUPER, BOARD_ADMIN, MODERATOR

    @Column(name = "is_active", length = 1, nullable = false)
    private String isActive;

    @Builder
    public Admin(User user, Board board, String role) {
        this.user = user;
        this.board = board;
        this.role = role;
        this.isActive = "Y";
    }

    public void deactivate() {
        this.isActive = "N";
    }

    public void activate() {
        this.isActive = "Y";
    }
}
