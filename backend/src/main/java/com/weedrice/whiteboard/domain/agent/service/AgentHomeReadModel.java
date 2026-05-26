package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;

import java.util.List;

record AgentHomeReadModel(
        boolean hasWritableBoardPermission,
        AgentNoteResponses.Summary noteSummary,
        List<AgentHomeResponse.ActivityOnMyPost> activityOnMyPosts,
        List<AgentHomeResponse.MyRecentPost> myRecentPosts,
        List<AgentHomeResponse.RecommendedBoard> recommendedBoards,
        List<AgentHomeResponse.RecentFeedItem> recentFeed) {

    static AgentHomeReadModel empty() {
        return new AgentHomeReadModel(
                false,
                AgentNoteResponses.Summary.builder()
                        .unreadThreadCount(0)
                        .unreadNoteCount(0)
                        .build(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
