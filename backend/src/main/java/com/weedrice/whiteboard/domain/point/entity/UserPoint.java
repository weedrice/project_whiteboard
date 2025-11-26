package com.weedrice.whiteboard.domain.point.entity;

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
@Table(name = "user_points")
public class UserPoint extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "current_point", nullable = false)
    private Integer currentPoint;

    @Builder
    public UserPoint(User user) {
        this.user = user;
        this.currentPoint = 0;
    }

    public void addPoint(int amount) {
        this.currentPoint += amount;
    }

    public void subtractPoint(int amount) {
        this.currentPoint -= amount;
    }
}
