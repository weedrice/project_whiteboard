package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "polls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poll extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "poll_id")
    private Long pollId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "question", nullable = false, length = 200)
    private String question;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "multiple_choice_enabled", nullable = false, length = 1)
    private Boolean multipleChoiceEnabled;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "anonymous_enabled", nullable = false, length = 1)
    private Boolean anonymousEnabled;

    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PollOption> options = new ArrayList<>();

    @Builder
    public Poll(Post post, String question, Boolean multipleChoiceEnabled, Boolean anonymousEnabled,
            LocalDateTime closesAt) {
        this.post = post;
        this.question = question;
        this.multipleChoiceEnabled = Boolean.TRUE.equals(multipleChoiceEnabled);
        this.anonymousEnabled = Boolean.TRUE.equals(anonymousEnabled);
        this.closesAt = closesAt;
    }

    public void addOption(PollOption option) {
        options.add(option);
    }
}
