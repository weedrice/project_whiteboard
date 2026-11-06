package com.weedrice.whiteboard.domain.user.port;

/** Consumer-owned boundary for restoring searchable content after account activation. */
public interface UserContentReindexPort {
    void reindexRestoredAuthor(Long userId);
}
