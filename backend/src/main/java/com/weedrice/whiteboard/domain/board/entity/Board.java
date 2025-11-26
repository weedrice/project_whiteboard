package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
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
        @Index(name = "idx_boards_active", columnList = "is_active, sort_order"),
        @Index(name = "idx_boards_creator", columnList = "creator_id")
})
public class Board extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_id")
    private Long boardId;

    @Column(name = "board_name", length = 100, nullable = false, unique = true)
    private String boardName;

    @Column(name = "description", length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "icon_url", length = 255)
    private String iconUrl;

    @Column(name = "banner_url", length = 255)
    private String bannerUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", length = 1, nullable = false)
    private String isActive;

    @Column(name = "allow_nsfw", length = 1, nullable = false)
    private String allowNsfw;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BoardCategory> categories = new ArrayList<>();

    @Builder
    public Board(String boardName, String description, User creator, String iconUrl, String bannerUrl, Integer sortOrder) {
        this.boardName = boardName;
        this.description = description;
        this.creator = creator;
        this.iconUrl = iconUrl;
        this.bannerUrl = bannerUrl;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isActive = "Y";
        this.allowNsfw = "N";
    }

    public void update(String description, String iconUrl, String bannerUrl, Integer sortOrder, Boolean allowNsfw) {
        this.description = description;
        this.iconUrl = iconUrl;
        this.bannerUrl = bannerUrl;
        this.sortOrder = sortOrder;
        this.allowNsfw = allowNsfw ? "Y" : "N";
    }
}
