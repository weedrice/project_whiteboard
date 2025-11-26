package com.weedrice.whiteboard.domain.sanction.dto;

import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class SanctionResponse {
    private List<SanctionSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class SanctionSummary {
        private Long sanctionId;
        private UserInfo targetUser;
        private AdminInfo admin;
        private String type;
        private String remark;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class UserInfo {
        private Long userId;
        private String displayName;
    }

    @Getter
    @Builder
    public static class AdminInfo {
        private Long adminId;
        private String displayName;
    }

    public static SanctionResponse from(Page<Sanction> sanctionPage) {
        List<SanctionSummary> content = sanctionPage.getContent().stream()
                .map(sanction -> SanctionSummary.builder()
                        .sanctionId(sanction.getSanctionId())
                        .targetUser(UserInfo.builder()
                                .userId(sanction.getTargetUser().getUserId())
                                .displayName(sanction.getTargetUser().getDisplayName())
                                .build())
                        .admin(AdminInfo.builder()
                                .adminId(sanction.getAdmin().getAdminId())
                                .displayName(sanction.getAdmin().getUser().getDisplayName())
                                .build())
                        .type(sanction.getType())
                        .remark(sanction.getRemark())
                        .startDate(sanction.getStartDate())
                        .endDate(sanction.getEndDate())
                        .createdAt(sanction.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return SanctionResponse.builder()
                .content(content)
                .page(sanctionPage.getNumber())
                .size(sanctionPage.getSize())
                .totalElements(sanctionPage.getTotalElements())
                .totalPages(sanctionPage.getTotalPages())
                .hasNext(sanctionPage.hasNext())
                .hasPrevious(sanctionPage.hasPrevious())
                .build();
    }
}
