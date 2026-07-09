package com.weedrice.whiteboard.domain.admin.dto;

import java.time.LocalDate;
import java.util.List;

public record DeepDashboardStatsDto(
        int days,
        List<DailyStats> daily,
        List<TopBoardStats> topBoards,
        ModerationStats moderation) {

    public record DailyStats(
            LocalDate date,
            long signups,
            long posts,
            long comments,
            long reports) {
    }

    public record TopBoardStats(
            Long boardId,
            String boardName,
            String boardUrl,
            long activityCount) {
    }

    public record ModerationStats(
            long pendingReports,
            long resolvedReports,
            long rejectedReports,
            long autoBlinds,
            long managerBlinds) {
    }
}
