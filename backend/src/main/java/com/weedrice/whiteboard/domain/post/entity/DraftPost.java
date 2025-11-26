package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "draft_posts", indexes = {
        @Index(name = "idx_draft_posts_user", columnList = "user_id, modified_at")
})
public class DraftPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "draft_id")
    private Long draftId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(name = "title", length = 200)
    private String title;

    @Lob
    @Column(name = "contents")
    private String contents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_post_id")
    private Post originalPost; // 수정 대상 게시글 ID

    @Builder
    public DraftPost(User user, Board board, String title, String contents, Post originalPost) {
        this.user = user;
        this.board = board;
        this.title = title;
        this.contents = contents;
        this.originalPost = originalPost;
    }

    public void updateDraft(Board board, String title, String contents, Post originalPost) {
        this.board = board;
        this.title = title;
        this.contents = contents;
        this.originalPost = originalPost;
    }
}
