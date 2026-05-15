package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserFeedRepositoryCustomImpl implements UserFeedRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Page<UserFeed> findVisibleByTargetUserOrderByCreatedAtDesc(
            UserFeedVisibilityCondition visibilityCondition,
            Pageable pageable) {
        String fromClause = UserFeedVisibilityQuerySpec.buildFromClause(visibilityCondition);
        TypedQuery<UserFeed> contentQuery = createVisibleFeedContentQuery(fromClause, visibilityCondition);
        if (pageable.isPaged()) {
            contentQuery.setFirstResult((int) pageable.getOffset());
            contentQuery.setMaxResults(pageable.getPageSize());
        }

        TypedQuery<Long> countQuery = entityManager.createQuery("SELECT COUNT(uf) " + fromClause, Long.class);
        UserFeedVisibilityQuerySpec.bindParameters(countQuery, visibilityCondition);

        List<UserFeed> content = contentQuery.getResultList();
        Long total = countQuery.getSingleResult();
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Slice<UserFeed> findVisibleSliceByTargetUserOrderByCreatedAtDesc(
            UserFeedVisibilityCondition visibilityCondition,
            Pageable pageable) {
        String fromClause = UserFeedVisibilityQuerySpec.buildFromClause(visibilityCondition);
        TypedQuery<UserFeed> contentQuery = createVisibleFeedContentQuery(fromClause, visibilityCondition);
        if (pageable.isPaged()) {
            contentQuery.setFirstResult((int) pageable.getOffset());
            contentQuery.setMaxResults(pageable.getPageSize() + 1);
        }

        List<UserFeed> content = contentQuery.getResultList();
        boolean hasNext = pageable.isPaged() && content.size() > pageable.getPageSize();
        List<UserFeed> sliceContent = hasNext
                ? new ArrayList<>(content.subList(0, pageable.getPageSize()))
                : content;
        return new SliceImpl<>(sliceContent, pageable, hasNext);
    }

    private TypedQuery<UserFeed> createVisibleFeedContentQuery(String fromClause,
                                                               UserFeedVisibilityCondition visibilityCondition) {
        TypedQuery<UserFeed> contentQuery = entityManager.createQuery(
                "SELECT uf " + fromClause + " ORDER BY uf.createdAt DESC, uf.feedId DESC",
                UserFeed.class);
        UserFeedVisibilityQuerySpec.bindParameters(contentQuery, visibilityCondition);
        return contentQuery;
    }
}
