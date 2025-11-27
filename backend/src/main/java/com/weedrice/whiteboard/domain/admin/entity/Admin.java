package com.weedrice.whiteboard.domain.admin.entity;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admins", uniqueConstraints = {
        @UniqueConstraint(name = "uk_admin_user_board", columnNames = {"user_id", "board_id"})
})
public class Admin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false) // nullable = false로 변경
    private Board board; // 이제 NULL일 수 없음

    @Column(name = "role", length = 50, nullable = false)
    private String role; // BOARD_ADMIN, MODERATOR (SUPER 역할은 User 엔티티로 이동)

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
