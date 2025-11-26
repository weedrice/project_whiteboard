package com.weedrice.whiteboard.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class IpBlockRequest {
    @NotBlank
    private String ipAddress;
    private String reason;
    private LocalDateTime endDate; // null이면 영구 차단
}
