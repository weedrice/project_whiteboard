package com.weedrice.whiteboard.domain.post.port;

/** Consumer-owned boundary for validating an optional agent actor in post mutations. */
public interface PostAgentWritePort {

    Long resolveOwnedActiveAgentId(Long ownerUserId, Long requestedAgentId);
}
