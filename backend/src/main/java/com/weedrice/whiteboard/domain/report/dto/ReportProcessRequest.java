package com.weedrice.whiteboard.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportProcessRequest {
    @NotBlank
    private String status; // RESOLVED, REJECTED
    private String remark;
}
