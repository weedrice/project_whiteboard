package com.weedrice.whiteboard.domain.actor;

public interface ActorReadPort {
    AuthorSnapshot resolveAuthor(ContentActorRef actorRef);
}
