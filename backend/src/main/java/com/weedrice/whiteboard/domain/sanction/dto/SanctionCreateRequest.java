package com.weedrice.whiteboard.domain.sanction.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class SanctionCreateRequest {
    private Long targetUserId;
    private String type; // WARNING, MUTE, BAN
    private String remark;
    private LocalDateTime endDate;
    private Long contentId; // Optional
    private String contentType; // Optional
}