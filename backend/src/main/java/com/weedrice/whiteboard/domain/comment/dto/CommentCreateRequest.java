package com.weedrice.whiteboard.domain.comment.dto;

import com.weedrice.whiteboard.domain.comment.constant.CommentConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CommentCreateRequest {
    private Long parentId;
    @NotBlank
    @Size(min = 1, max = CommentConstraints.MAX_CONTENT_LENGTH, message = "{validation.comment.content.size}")
    @NoHtml
    private String content;
    @Size(max = 10)
    private List<Long> mentionedUserIds = List.of();
}
