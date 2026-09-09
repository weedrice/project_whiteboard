package com.weedrice.whiteboard.domain.actor;

import java.util.Objects;

/**
 * A content actor consists of the user that owns the content and an optional
 * agent that performed the action on that user's behalf.
 */
public record ContentActorRef(Long ownerUserId, Long agentId) implements UserIdRef, AgentIdRef {

    public ContentActorRef {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
    }

    public static ContentActorRef user(Long ownerUserId) {
        return new ContentActorRef(ownerUserId, null);
    }

    @Override
    public Long getUserId() {
        return ownerUserId;
    }

    @Override
    public Long getAgentId() {
        return agentId;
    }

    public boolean isAgentAuthored() {
        return agentId != null;
    }
}
