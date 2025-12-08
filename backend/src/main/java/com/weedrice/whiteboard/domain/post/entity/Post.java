package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.user.entity.User;
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
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_board_created", columnList = "board_id, is_deleted, created_at"),
        @Index(name = "idx_posts_board_notice", columnList = "board_id, is_notice, created_at"),
        @Index(name = "idx_posts_user", columnList = "user_id, is_deleted, created_at"),
        @Index(name = "idx_posts_category", columnList = "category_id"),
        @Index(name = "idx_posts_popular", columnList = "board_id, is_deleted, like_count, created_at")
})
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private BoardCategory category;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "contents", nullable = false, columnDefinition = "TEXT")
    private String contents;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_deleted", length = 1, nullable = false)
    private Boolean isDeleted;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_notice", length = 1, nullable = false)
    private Boolean isNotice;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_nsfw", length = 1, nullable = false)
    private Boolean isNsfw;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_spoiler", length = 1, nullable = false)
    private Boolean isSpoiler;

    @Builder
    public Post(Board board, User user, BoardCategory category, String title, String contents, boolean isNotice, boolean isNsfw, boolean isSpoiler) {
        this.board = board;
        this.user = user;
        this.category = category;
        this.title = title;
        this.contents = contents;
        this.viewCount = 0;
        this.likeCount = 0;
        this.commentCount = 0;
        this.isDeleted = false;
        this.isNotice = isNotice;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        this.likeCount--;
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        this.commentCount--;
    }

    public void updatePost(BoardCategory category, String title, String contents, boolean isNsfw, boolean isSpoiler) {
        this.category = category;
        this.title = title;
        this.contents = contents;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
    }

    public void deletePost() {
        this.isDeleted = true;
    }
}
