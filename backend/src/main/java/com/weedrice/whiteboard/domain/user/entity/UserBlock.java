package com.weedrice.whiteboard.domain.user.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_blocks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_blocks", columnNames = {"user_id", "target_id"})
        },
        indexes = {
                @Index(name = "idx_user_blocks_target", columnList = "target_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_id")
    private Long relationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    @Builder
    public UserBlock(User user, User target) {
        this.user = user;
        this.target = target;
    }
}