package com.weedrice.whiteboard.domain.comment.dto;

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
    @Size(min = 1, max = 5000, message = "댓글은 1자 이상 5,000자 이하여야 합니다")
    @NoHtml
    private String content;
}
