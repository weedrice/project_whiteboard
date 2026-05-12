package com.weedrice.whiteboard.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.weedrice.whiteboard.domain.report.constant.ReportConstraints;
import com.weedrice.whiteboard.domain.report.entity.ReportStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportProcessRequest {
    @NotNull
    private ReportStatus status;
    @Size(max = ReportConstraints.MAX_REMARK_LENGTH)
    private String remark;

    @JsonIgnore
    @AssertTrue
    public boolean isTerminalStatusValid() {
        return status == null || status.isTerminal();
    }
}
