package com.weedrice.whiteboard.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportCreateRequest {
    @NotBlank
    private String targetType; // POST, COMMENT, USER
    @NotNull
    private Long targetId;
    @NotBlank
    private String reasonType; // SPAM, ABUSE, ADULT 등 (공통코드)
    private String remark;
    private String contents;
}
