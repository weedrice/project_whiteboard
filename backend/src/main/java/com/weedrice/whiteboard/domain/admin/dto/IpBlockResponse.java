package com.weedrice.whiteboard.domain.admin.dto;

import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class IpBlockResponse {
    private String ipAddress;
    private String reason;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private AdminInfo admin;

    @Getter
    @Builder
    public static class AdminInfo {
        private Long adminId;
        private String displayName;
    }

    public static IpBlockResponse from(IpBlock ipBlock) {
        AdminInfo adminInfo = AdminInfo.builder()
                .adminId(ipBlock.getAdmin().getAdminId())
                .displayName(ipBlock.getAdmin().getUser().getDisplayName())
                .build();

        return IpBlockResponse.builder()
                .ipAddress(ipBlock.getIpAddress())
                .reason(ipBlock.getReason())
                .startDate(ipBlock.getStartDate())
                .endDate(ipBlock.getEndDate())
                .admin(adminInfo)
                .build();
    }

    public static List<IpBlockResponse> from(List<IpBlock> ipBlocks) {
        return ipBlocks.stream()
                .map(IpBlockResponse::from)
                .collect(Collectors.toList());
    }
}
