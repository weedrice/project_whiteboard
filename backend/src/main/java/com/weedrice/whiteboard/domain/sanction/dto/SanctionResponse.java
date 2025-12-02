package com.weedrice.whiteboard.domain.sanction.dto;

import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SanctionResponse {
    private Long sanctionId;
    private Long targetUserId;
    private String targetUserDisplayName;
    private Long adminId;
    private String type;
    private String remark;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long contentId;
    private String contentType;

    public static SanctionResponse from(Sanction sanction) {
        return SanctionResponse.builder()
                .sanctionId(sanction.getSanctionId())
                .targetUserId(sanction.getTargetUser().getUserId())
                .targetUserDisplayName(sanction.getTargetUser().getDisplayName())
                .adminId(sanction.getAdmin().getAdminId())
                .type(sanction.getType())
                .remark(sanction.getRemark())
                .startDate(sanction.getStartDate())
                .endDate(sanction.getEndDate())
                .contentId(sanction.getContentId())
                .contentType(sanction.getContentType())
                .build();
    }
}