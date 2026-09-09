package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "scraps")
@IdClass(ScrapId.class)
public class Scrap extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(name = "remark", length = 255)
    private String remark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private ScrapFolder folder;

    @Builder
    public Scrap(Long userId, Post post, String remark, ScrapFolder folder) {
        this.userId = userId;
        this.post = post;
        this.remark = remark;
        this.folder = folder;
    }

    public static class ScrapBuilder {
        public ScrapBuilder user(UserIdRef user) {
            this.userId = user == null ? null : user.getUserId();
            return this;
        }
    }

    public void moveToFolder(ScrapFolder folder) {
        this.folder = folder;
    }
}
