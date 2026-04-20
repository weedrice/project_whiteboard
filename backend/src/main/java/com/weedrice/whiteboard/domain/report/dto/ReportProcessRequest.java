package com.weedrice.whiteboard.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportProcessRequest {
    @NotBlank
    @Pattern(regexp = "RESOLVED|REJECTED")
    private String status;
    private String remark;
}
