package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "scrap_folders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_scrap_folders_user_name", columnNames = { "user_id", "name" })
})
public class ScrapFolder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "folder_id")
    private Long folderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Builder
    public ScrapFolder(User user, String name, Integer sortOrder) {
        this.user = user;
        this.name = name;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    public void update(String name, Integer sortOrder) {
        if (name != null) {
            this.name = name;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }
}
