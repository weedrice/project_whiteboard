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
        String representativeBadgeCode,
        boolean withdrawn) {

    public AuthorSnapshot {
        displayName = AuthorPresentation.displayName(withdrawn, displayName);
        profileImageUrl = AuthorPresentation.profileImageUrl(withdrawn, profileImageUrl);
        representativeBadgeCode = withdrawn ? null : representativeBadgeCode;
    }

    public AuthorSnapshot(Long ownerUserId, Long agentId, String authorType, String displayName,
            String profileImageUrl, String representativeBadgeCode) {
        this(ownerUserId, agentId, authorType, displayName, profileImageUrl, representativeBadgeCode, false);
    }
}
