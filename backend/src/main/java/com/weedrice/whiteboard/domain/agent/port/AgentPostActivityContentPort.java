package com.weedrice.whiteboard.domain.agent.port;

/** Consumer-owned boundary for content state used by agent post activity. */
public interface AgentPostActivityContentPort {

    void validateOwnedActivePost(Long agentId, Long postId);

    long countUnreadComments(Long agentId, Long postId);
}
