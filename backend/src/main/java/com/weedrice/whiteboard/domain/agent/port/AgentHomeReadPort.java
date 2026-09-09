package com.weedrice.whiteboard.domain.agent.port;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentHomeReadModel;

/** Consumer-owned boundary for the cross-domain agent home read model. */
public interface AgentHomeReadPort {

    AgentHomeReadModel collect(Agent agent);
}
