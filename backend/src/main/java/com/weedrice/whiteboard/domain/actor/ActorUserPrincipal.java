package com.weedrice.whiteboard.domain.actor;

/** Minimal user identity and privilege contract used across domain boundaries. */
public interface ActorUserPrincipal extends UserIdRef {

    boolean isUsableSuperAdmin();
}
