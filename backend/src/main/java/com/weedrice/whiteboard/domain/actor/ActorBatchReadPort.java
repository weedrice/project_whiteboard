package com.weedrice.whiteboard.domain.actor;

import java.util.Collection;
import java.util.Map;

public interface ActorBatchReadPort {
    Map<ContentActorRef, AuthorSnapshot> resolveAuthors(Collection<ContentActorRef> actorRefs);
}
