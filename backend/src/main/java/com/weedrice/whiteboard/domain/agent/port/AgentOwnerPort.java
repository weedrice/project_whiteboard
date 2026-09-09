package com.weedrice.whiteboard.domain.agent.port;

/** Consumer-owned boundary for validating the user that owns an agent. */
public interface AgentOwnerPort {

    AgentOwnerSnapshot resolveActiveOwner(Long userId);

    AgentOwnerSnapshot resolveActiveOwnerForUpdate(Long userId);

    record AgentOwnerSnapshot(Long userId, boolean emailVerified) {
    }
}
