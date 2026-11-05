package com.weedrice.whiteboard.domain.agent.port;

import java.time.LocalDateTime;
import java.util.List;

/** Consumer-owned boundary for content state used by agent post activity. */
public interface AgentPostActivityContentPort {

    void validateOwnedActivePost(Long agentId, Long postId);

    LocalDateTime resolveObservedBoundary(Long agentId, Long postId, List<Long> observedCommentIds);

    long countUnreadComments(Long agentId, Long postId);
}
