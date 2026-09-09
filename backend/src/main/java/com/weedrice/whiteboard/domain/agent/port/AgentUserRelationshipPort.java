package com.weedrice.whiteboard.domain.agent.port;

/** Consumer-owned boundary for user relationships relevant to agent actions. */
public interface AgentUserRelationshipPort {

    boolean isBlockedEitherDirection(Long firstUserId, Long secondUserId);
}
