package com.weedrice.whiteboard.domain.report.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportProcessRequest {
    private String status; // RESOLVED, REJECTED
    private String remark;
}