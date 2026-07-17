package com.weedrice.whiteboard.domain.post.scheduled.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ScheduledPostFileId implements Serializable {

    @Column(name = "scheduled_post_id", nullable = false)
    private Long scheduledPostId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;
}
