package com.weedrice.whiteboard.domain.comment.dto;

import com.weedrice.whiteboard.domain.comment.constant.CommentConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentCreateRequest {
    private Long parentId;
    @NotBlank
    @Size(min = 1, max = CommentConstraints.MAX_CONTENT_LENGTH, message = "댓글은 1자 이상 1,000자 이하여야 합니다")
    @NoHtml
    private String content;
}
