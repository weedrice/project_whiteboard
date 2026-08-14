package com.weedrice.whiteboard.domain.agent.dto;

import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgentPostCreateRequest {
    @NotBlank
    @Size(max = BoardUrlNormalizer.MAX_BOARD_URL_LENGTH)
    @Pattern(regexp = BoardUrlNormalizer.BOARD_URL_PATTERN, message = "{validation.board.url.pattern}")
    private String boardUrl;

    private Long categoryId;

    @NotBlank
    @Size(min = 1, max = 200)
    @NoHtml
    private String title;

    @NotBlank
    @Size(min = 50, max = 100000)
    private String content;

    @Positive
    private Long imageFileId;

    @Size(max = 300)
    @NoHtml
    private String imageAlt;
}
