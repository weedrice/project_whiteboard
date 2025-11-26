package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "board_categories", indexes = {
        @Index(name = "idx_board_categories_board", columnList = "board_id, is_active, sort_order")
})
public class BoardCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", length = 1, nullable = false)
    private String isActive;

    @Builder
    public BoardCategory(Board board, String name, Integer sortOrder) {
        this.board = board;
        this.name = name;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isActive = "Y";
    }
}
