package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board_ai_info")
public class BoardAiInfo extends BaseTimeEntity {

    @Id
    @Column(name = "board_id")
    private Long boardId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(name = "guide_prompt", columnDefinition = "TEXT")
    private String guidePrompt;

    @Builder
    public BoardAiInfo(Board board, String guidePrompt) {
        this.board = board;
        this.guidePrompt = guidePrompt;
    }

    public void updateGuidePrompt(String guidePrompt) {
        this.guidePrompt = guidePrompt;
    }
}
