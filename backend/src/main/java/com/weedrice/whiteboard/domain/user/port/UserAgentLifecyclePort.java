package com.weedrice.whiteboard.domain.user.port;

/** Consumer-owned boundary for revoking agent access during a user lifecycle change. */
public interface UserAgentLifecyclePort {

    void suspendAllForUser(Long userId);
}
