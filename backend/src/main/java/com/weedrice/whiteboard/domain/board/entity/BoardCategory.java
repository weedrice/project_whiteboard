package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
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

    @Column(name = "min_write_role", length = 20)
    private String minWriteRole;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_active", length = 1, nullable = false)
    private Boolean isActive;

    @Builder
    public BoardCategory(Board board, String name, Integer sortOrder, String minWriteRole) {
        this.board = board;
        this.name = name;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.minWriteRole = minWriteRole != null ? minWriteRole : Role.USER;
        this.isActive = true;
    }

    public void update(String name, Integer sortOrder, String minWriteRole) {
        this.name = name;
        this.sortOrder = sortOrder;
        if (minWriteRole != null) {
            this.minWriteRole = minWriteRole;
        }
    }

    public void update(String name, Integer sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
