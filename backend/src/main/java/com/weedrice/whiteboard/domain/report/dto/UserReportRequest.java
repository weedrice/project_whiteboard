package com.weedrice.whiteboard.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserReportRequest {

    @NotNull
    private Long targetUserId;

    @NotBlank
    private String reason;

    private String reasonType;

    private String link;
}
