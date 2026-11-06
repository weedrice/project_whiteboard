package com.weedrice.whiteboard.domain.actor.query;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** Batch eligibility for projections that retain hidden actors as placeholders. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentAuthorVisibilityRepository {

    private final EntityManager entityManager;

    public Set<Long> findVisibleUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        var candidates = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(entityManager.createQuery("""
                SELECT candidate.userId FROM User candidate
                WHERE candidate.userId IN :candidateIds
                  AND candidate.userId IN (
                """ + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + ")", Long.class)
                .setParameter("candidateIds", candidates)
                .getResultList());
    }
}
