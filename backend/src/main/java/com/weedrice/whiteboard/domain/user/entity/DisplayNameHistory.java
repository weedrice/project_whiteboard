package com.weedrice.whiteboard.domain.user.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "display_name_histories",
        indexes = {
                @Index(name = "idx_display_name_histories_user", columnList = "user_id, changed_at"),
                @Index(name = "idx_display_name_histories_name", columnList = "previous_name")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisplayNameHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "previous_name", nullable = false, length = 50)
    private String previousName;

    @Column(name = "new_name", nullable = false, length = 50)
    private String newName;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Builder
    public DisplayNameHistory(User user, String previousName, String newName) {
        this.user = user;
        this.previousName = previousName;
        this.newName = newName;
        this.changedAt = LocalDateTime.now();
    }
}
