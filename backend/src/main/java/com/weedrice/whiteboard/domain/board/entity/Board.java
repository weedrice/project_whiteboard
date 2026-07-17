package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "boards", indexes = {
        @Index(name = "idx_boards_active", columnList = "is_active, is_public, sort_order"),
        @Index(name = "idx_boards_creator", columnList = "creator_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_boards_board_name", columnNames = "board_name"),
        @UniqueConstraint(name = "uk_boards_board_url", columnNames = "board_url")
})
public class Board extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private Long boardId;

    @Column(name = "board_name", length = 100, nullable = false)
    private String boardName;

    @Column(name = "board_url", length = 100, nullable = false)
    private String boardUrl;

    @Column(name = "description", length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "icon_url", length = 255)
    private String iconUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_active", length = 1, nullable = false)
    private Boolean isActive;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "allow_nsfw", length = 1, nullable = false)
    private Boolean allowNsfw;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_public", length = 1, nullable = false, columnDefinition = "varchar(1) default 'Y'")
    private Boolean isPublic;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "agent_use_yn", length = 1, columnDefinition = "varchar(1) default 'N'")
    private Boolean agentUseYn;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BoardCategory> categories = new ArrayList<>();

    @Builder
    public Board(String boardName, String boardUrl, String description, User creator, String iconUrl,
            Integer sortOrder, Boolean isPublic, Boolean agentUseYn) {
        this.boardName = boardName;
        this.boardUrl = boardUrl;
        this.description = description;
        this.creator = creator;
        this.iconUrl = iconUrl;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isActive = true;
        this.allowNsfw = false;
        this.isPublic = isPublic != null ? isPublic : true;
        this.agentUseYn = agentUseYn != null ? agentUseYn : false;
    }

    public void update(String boardName, String description, String iconUrl, Integer sortOrder, Boolean allowNsfw,
            Boolean isActive, Boolean isPublic, Boolean agentUseYn) {
        this.boardName = boardName;
        this.description = description;
        this.iconUrl = iconUrl;
        this.sortOrder = sortOrder;
        this.allowNsfw = allowNsfw;
        if (isActive != null) {
            this.isActive = isActive;
        }
        if (isPublic != null) {
            this.isPublic = isPublic;
        }
        if (agentUseYn != null) {
            this.agentUseYn = agentUseYn;
        }
    }

    public void update(String boardName, String description, String iconUrl, Integer sortOrder, Boolean allowNsfw,
            Boolean isActive, Boolean isPublic) {
        update(boardName, description, iconUrl, sortOrder, allowNsfw, isActive, isPublic, null);
    }

    public void updateBoardUrl(String boardUrl) {
        this.boardUrl = boardUrl;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isAgentEnabled() {
        return Boolean.TRUE.equals(agentUseYn);
    }
}
