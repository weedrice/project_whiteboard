package com.weedrice.whiteboard.domain.post.entity;

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
@Table(name = "poll_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PollOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @Column(name = "option_text", nullable = false, length = 100)
    private String optionText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    public PollOption(Poll poll, String optionText, int sortOrder) {
        this.poll = poll;
        this.optionText = optionText;
        this.sortOrder = sortOrder;
    }
}
