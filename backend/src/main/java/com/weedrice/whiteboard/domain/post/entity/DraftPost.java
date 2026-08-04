package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.post.converter.LongListJsonConverter;
import com.weedrice.whiteboard.domain.post.converter.PollRequestJsonConverter;
import com.weedrice.whiteboard.domain.post.converter.StringListJsonConverter;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    @Version
    @Column(name = "entity_version", nullable = false)
    private Long version;

    @Column(name = "client_draft_key", length = 64)
    private String clientDraftKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private BoardCategory category;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "contents", columnDefinition = "TEXT")
    private String contents;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "tags_json", columnDefinition = "TEXT")
    private List<String> tags;

    @Column(name = "is_notice", nullable = false)
    private boolean isNotice;

    @Column(name = "is_nsfw", nullable = false)
    private boolean isNsfw;

    @Column(name = "is_spoiler", nullable = false)
    private boolean isSpoiler;

    @Column(name = "is_secret", nullable = false)
    private boolean isSecret;

    @Convert(converter = LongListJsonConverter.class)
    @Column(name = "file_ids_json", columnDefinition = "TEXT")
    private List<Long> fileIds;

    @Convert(converter = PollRequestJsonConverter.class)
    @Column(name = "poll_json", columnDefinition = "TEXT")
    private PollRequest poll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private PostSeries series;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_post_id")
    private Post originalPost;

    @Builder
    public DraftPost(User user, Board board, BoardCategory category, String clientDraftKey,
            String title, String contents, List<String> tags,
            boolean isNotice, boolean isNsfw, boolean isSpoiler, boolean isSecret, List<Long> fileIds,
            PollRequest poll, PostSeries series, Post originalPost) {
        this.user = user;
        this.board = board;
        this.clientDraftKey = clientDraftKey;
        this.category = category;
        this.title = title;
        this.contents = contents;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.isNotice = isNotice;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
        this.isSecret = isSecret;
        this.fileIds = fileIds != null ? new ArrayList<>(fileIds) : new ArrayList<>();
        this.poll = poll;
        this.series = series;
        this.originalPost = originalPost;
    }

    public void adoptClientDraftKey(String clientDraftKey) {
        if (this.clientDraftKey == null) {
            this.clientDraftKey = clientDraftKey;
        }
    }

    public void replaceFileIds(List<Long> fileIds) {
        this.fileIds = fileIds != null ? new ArrayList<>(fileIds) : new ArrayList<>();
    }

    public void updateDraft(Board board, BoardCategory category, String title, String contents, List<String> tags,
            boolean isNotice, boolean isNsfw, boolean isSpoiler, boolean isSecret, List<Long> fileIds,
            PollRequest poll, PostSeries series, Post originalPost) {
        this.board = board;
        this.category = category;
        this.title = title;
        this.contents = contents;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.isNotice = isNotice;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
        this.isSecret = isSecret;
        this.fileIds = fileIds != null ? new ArrayList<>(fileIds) : new ArrayList<>();
        this.poll = poll;
        this.series = series;
        this.originalPost = originalPost;
    }
}
