package com.weedrice.whiteboard.domain.sanction.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class SanctionCreateRequest {
    private Long targetUserId;
    private String type; // WARNING, MUTE, BAN
    @Size(max = 255)
    private String remark;
    private LocalDateTime endDate;
    private Long contentId; // Optional
    private String contentType; // Optional
}
