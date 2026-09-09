package com.weedrice.whiteboard.domain.actor;

/**
 * Immutable author data exposed across domain boundaries.
 */
public record AuthorSnapshot(
        Long ownerUserId,
        Long agentId,
        String authorType,
        String displayName,
        String profileImageUrl,
        String representativeBadgeCode) {
}
