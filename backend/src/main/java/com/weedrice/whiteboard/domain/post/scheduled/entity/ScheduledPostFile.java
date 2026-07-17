package com.weedrice.whiteboard.domain.post.scheduled.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scheduled_post_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledPostFile {

    @EmbeddedId
    private ScheduledPostFileId id;

    @Column(name = "file_order", nullable = false)
    private int fileOrder;

    public ScheduledPostFile(Long scheduledPostId, Long fileId, int fileOrder) {
        this.id = new ScheduledPostFileId(scheduledPostId, fileId);
        this.fileOrder = fileOrder;
    }
}
