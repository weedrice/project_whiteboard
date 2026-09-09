package com.weedrice.whiteboard.domain.agent.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Consumer-owned boundary for cross-domain data used to evaluate agent policy. */
public interface AgentPolicyDataPort {

    AgentPolicyData resolve(
            Long agentId,
            Long ownerUserId,
            LocalDateTime activityStart,
            LocalDateTime activityEnd,
            Set<String> restrictionTypes,
            LocalDateTime now);

    record AgentPolicyData(
            long postsToday,
            long commentsToday,
            boolean ownerActive,
            List<RestrictionSnapshot> restrictions) {
    }

    record RestrictionSnapshot(String type, String remark, LocalDateTime endDate) {
    }
}
