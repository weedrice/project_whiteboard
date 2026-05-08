package com.weedrice.whiteboard.domain.report.dto;

import com.weedrice.whiteboard.domain.report.entity.ReportReasonType;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 255)
    private String remark;
    private String contents;
}
