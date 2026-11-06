package com.weedrice.whiteboard.domain.comment.query;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibility;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Public batch read boundary for actual visible comments, excluding hidden ancestor placeholders. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentVisibleCountReader {
    private final EntityManager entityManager;

    public Map<Long, Integer> countByPostIds(Collection<Long> postIds, Collection<Long> blockedUserIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> blocked = blockedUserIds == null ? List.of()
                : blockedUserIds.stream().filter(Objects::nonNull).distinct().toList();
        String sql = """
                SELECT c.postId, COUNT(c)
                FROM Comment c
                WHERE c.postId IN :postIds
                  AND c.isDeleted = false
                  AND c.isBlinded = false
                  AND c.userId IN (
                """ + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + ")"
                + (blocked.isEmpty() ? "" : " AND c.userId NOT IN :blockedUserIds")
                + " GROUP BY c.postId";
        var query = entityManager.createQuery(sql, Object[].class).setParameter("postIds", ids);
        if (!blocked.isEmpty()) {
            query.setParameter("blockedUserIds", blocked);
        }
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Object[] row : query.getResultList()) {
            counts.put(((Number) row[0]).longValue(), Math.toIntExact(((Number) row[1]).longValue()));
        }
        return counts;
    }
}
