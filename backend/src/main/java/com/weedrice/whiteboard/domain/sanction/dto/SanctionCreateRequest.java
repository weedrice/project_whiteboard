package com.weedrice.whiteboard.domain.sanction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class SanctionCreateRequest {
    @NotNull
    private Long targetUserId;

    @NotBlank
    private String type; // WARNING, MUTE, BAN

    private String remark;
    private LocalDateTime endDate; // null이면 영구 제재
}
