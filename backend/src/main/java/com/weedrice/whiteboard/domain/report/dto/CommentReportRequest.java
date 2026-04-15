package com.weedrice.whiteboard.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentReportRequest {

    @NotNull
    private Long targetCommentId;

    @NotBlank
    private String reason;

    private String reasonType;
}
