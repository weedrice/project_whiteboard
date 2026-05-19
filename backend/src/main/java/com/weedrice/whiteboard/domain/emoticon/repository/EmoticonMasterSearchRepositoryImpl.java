package com.weedrice.whiteboard.domain.emoticon.repository;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EmoticonMasterSearchRepositoryImpl implements EmoticonMasterSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<EmoticonMaster> searchActive(EmoticonSearchCondition condition, Pageable pageable) {
        Assert.notNull(condition, "condition must not be null");
        Assert.notNull(condition.searchType(), "searchType must not be null");
        Assert.notNull(condition.sortType(), "sortType must not be null");

        NativeSearchQueryParts queryParts = buildQueryParts(condition);
        long total = fetchCount(queryParts);
        if (total == 0L) {
            return new PageImpl<>(List.of(), pageable, 0L);
        }

        Query contentQuery = entityManager.createNativeQuery(
                queryParts.selectSql() + queryParts.orderBySql(),
                EmoticonMaster.class);
        applyParameters(contentQuery, queryParts.params());
        if (pageable.isPaged()) {
            contentQuery.setFirstResult((int) pageable.getOffset());
            contentQuery.setMaxResults(pageable.getPageSize());
        }

        @SuppressWarnings("unchecked")
        List<EmoticonMaster> content = contentQuery.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private NativeSearchQueryParts buildQueryParts(EmoticonSearchCondition condition) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", condition.keyword());

        String fromClause = switch (condition.searchType()) {
            case CREATOR -> " FROM emoticon_masters em JOIN users u ON em.creator_id = u.user_id";
            case ALL -> " FROM emoticon_masters em LEFT JOIN users u ON em.creator_id = u.user_id";
            case NAME, TAG -> " FROM emoticon_masters em";
        };

        String keywordClause = switch (condition.searchType()) {
            case NAME -> "LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))";
            case CREATOR -> "LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))";
            case TAG -> ":keyword = ANY(em.tags)";
            case ALL -> """
                    (
                        LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR :keyword = ANY(em.tags)
                    )
                    """;
        };

        String projection = condition.searchType() == EmoticonSearchCondition.SearchType.ALL
                ? "SELECT DISTINCT em.*"
                : "SELECT em.*";
        String countProjection = condition.searchType() == EmoticonSearchCondition.SearchType.ALL
                ? "SELECT COUNT(DISTINCT em.emoticon_id)"
                : "SELECT COUNT(*)";
        String whereClause = " WHERE em.is_active = 'Y' AND " + keywordClause;

        return new NativeSearchQueryParts(
                projection + fromClause + whereClause,
                countProjection + fromClause + whereClause,
                buildOrderBy(condition.sortType()),
                params);
    }

    private String buildOrderBy(EmoticonSearchCondition.SortType sortType) {
        return switch (sortType) {
            case POPULAR -> " ORDER BY em.purchase_count DESC, em.created_at DESC";
            case OLDEST -> " ORDER BY em.created_at ASC";
            case LATEST -> " ORDER BY em.created_at DESC";
        };
    }

    private long fetchCount(NativeSearchQueryParts queryParts) {
        Query countQuery = entityManager.createNativeQuery(queryParts.countSql());
        applyParameters(countQuery, queryParts.params());
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    private void applyParameters(Query query, Map<String, Object> params) {
        params.forEach(query::setParameter);
    }

    private record NativeSearchQueryParts(
            String selectSql,
            String countSql,
            String orderBySql,
            Map<String, Object> params) {
    }
}
