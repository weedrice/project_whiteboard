package com.weedrice.whiteboard.domain.user.port;

public interface UserScheduledPostLifecyclePort {
    /** Cancel unpublished schedules in the caller's transaction while its user row is locked. */
    void cancelUnpublishedForLockedUser(Long userId);
}
