package com.weedrice.whiteboard.domain.actor;

public interface ActorWritePort {
    ContentActorRef validateForWrite(ContentActorRef actorRef);
}
