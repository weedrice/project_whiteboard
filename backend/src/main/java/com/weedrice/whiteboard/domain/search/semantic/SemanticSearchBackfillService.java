package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SemanticSearchBackfillService {

    private static final String REPAIR_ORDER = """
            ORDER BY CASE WHEN j.status = 'FAILED' THEN 1 ELSE 0 END ASC,
                     source.created_at ASC, source.content_id ASC LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SemanticSearchJobRepository jobRepository;

    @Transactional
    public int enqueueBackfill(String contentType, int limit) {
        SemanticSearchContentType normalizedContentType = SemanticSearchContentType.from(contentType);
        if (limit < 1 || limit > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        int count = 0;
        if (normalizedContentType.includesPosts()) {
            count += enqueueType("POST", limit);
        }
        if (normalizedContentType.includesComments()) {
            count += enqueueType("COMMENT", limit);
        }
        return count;
    }

    private int enqueueType(String type, int limit) {
        int repairLimit = limit - jobRepository.probeFailedDeletes(type, limit);
        if (repairLimit == 0) {
            return limit;
        }

        while (true) {
            List<Long> repairs = jdbcTemplate.queryForList(repairableSql(type) + REPAIR_ORDER,
                    Long.class, type, type, new SqlArrayValue("bigint"), repairLimit);
            boolean canExpand = repairLimit < limit && repairs.size() == repairLimit;
            if (canExpand) {
                jdbcTemplate.execute("SAVEPOINT backfill_repairs");
            }
            List<Long> inserted = repairs.isEmpty() ? List.of() : jobRepository.reserveJobKeys(type, repairs);
            int redriven = jobRepository.redriveUnlockedDeletes(type, limit);
            int remaining = limit - redriven;
            if (canExpand && remaining > repairLimit) {
                // The delete slots changed owners. Release every key from this attempt
                // before reserving a larger page, which may include lower IDs.
                jdbcTemplate.execute("ROLLBACK TO SAVEPOINT backfill_repairs");
                jdbcTemplate.execute("RELEASE SAVEPOINT backfill_repairs");
                repairLimit = remaining;
                continue;
            }
            if (canExpand) {
                jdbcTemplate.execute("RELEASE SAVEPOINT backfill_repairs");
            }
            return redriven + enqueueReservedRepairs(type, repairs, inserted, remaining);
        }
    }

    private int enqueueReservedRepairs(String type, List<Long> repairs, List<Long> inserted, int remaining) {
        // A fresh statement sees changes committed while waiting for a key. Our provisional
        // rows stand in for absent jobs and must not make those repairs appear already pending.
        List<Long> eligible = remaining == 0 || repairs.isEmpty() ? List.of() : jdbcTemplate.queryForList(
                repairableSql(type) + " AND source.content_id = ANY(?) " + REPAIR_ORDER,
                Long.class, type, type, new SqlArrayValue("bigint", inserted.toArray()),
                new SqlArrayValue("bigint", repairs.toArray()), remaining);
        if (!eligible.isEmpty()) {
            jobRepository.enqueueAll(type, eligible, SemanticSearchIndexAction.UPSERT);
        }
        Set<Long> queued = Set.copyOf(eligible);
        jobRepository.discardUnusedReservations(type, inserted.stream().filter(id -> !queued.contains(id)).toList());
        return eligible.size();
    }

    private String repairableSql(String type) {
        return "SELECT source.content_id FROM (" + SemanticSearchSqlFragments.indexableSources(type) + """
                ) source
                LEFT JOIN semantic_search_embeddings e ON e.content_type = ? AND e.content_id = source.content_id
                LEFT JOIN semantic_search_jobs j ON j.content_type = ? AND j.content_id = source.content_id
                    AND NOT (j.content_id = ANY(?))
                WHERE (j.job_id IS NULL OR j.status = 'COMPLETED' OR (j.status = 'FAILED' AND j.action = 'UPSERT'))
                  AND (e.embedding_id IS NULL OR e.deleted_at IS NOT NULL OR (j.status = 'FAILED' AND j.action = 'UPSERT'))
                """;
    }
}
