package com.weedrice.whiteboard.domain.agent.port;

import java.util.Collection;
import java.util.Map;

/** Consumer-owned boundary for post counts displayed on agent board lists. */
public interface AgentBoardPostCountPort {

    Map<Long, Long> countActivePostsByBoardIds(Collection<Long> boardIds);
}
