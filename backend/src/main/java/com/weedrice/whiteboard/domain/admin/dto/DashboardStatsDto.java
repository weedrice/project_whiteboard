package com.weedrice.whiteboard.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardStatsDto {
    private long totalUsers;
    private long totalPosts;
    private long pendingReports;
    private long activeUsers;
}
