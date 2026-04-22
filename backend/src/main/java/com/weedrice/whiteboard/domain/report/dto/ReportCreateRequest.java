package com.weedrice.whiteboard.domain.report.dto;

import com.weedrice.whiteboard.domain.report.entity.ReportReasonType;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportCreateRequest {
    @NotNull
    private ReportTargetType targetType;
    @NotNull
    private Long targetId;
    @NotNull
    private ReportReasonType reasonType;
    private String remark;
    private String contents;
}
