package com.weedrice.whiteboard.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Size(max = 255)
    private String link;
}
