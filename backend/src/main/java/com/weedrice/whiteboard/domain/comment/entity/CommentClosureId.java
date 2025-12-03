package com.weedrice.whiteboard.domain.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class CommentClosureId implements Serializable {

    @Column(name = "ancestor_id")
    private Long ancestorId;

    @Column(name = "descendant_id")
    private Long descendantId;
}
