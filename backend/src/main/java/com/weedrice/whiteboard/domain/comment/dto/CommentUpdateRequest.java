package com.weedrice.whiteboard.domain.comment.dto;

import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentUpdateRequest {
    @NotBlank
    @Size(min = 1, max = 1000, message = "댓글은 1자 이상 1,000자 이하여야 합니다")
    @NoHtml
    private String content;
}
