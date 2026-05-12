package com.weedrice.whiteboard.domain.report.dto;

import com.weedrice.whiteboard.domain.report.constant.ReportConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentReportRequest {

    @NotNull
    private Long targetCommentId;

    @NotBlank
    @Size(max = ReportConstraints.MAX_CONTENTS_LENGTH)
    private String reason;

    private String reasonType;
}
