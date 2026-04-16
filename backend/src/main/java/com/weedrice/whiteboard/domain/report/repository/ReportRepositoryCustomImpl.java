package com.weedrice.whiteboard.domain.report.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.report.entity.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.weedrice.whiteboard.domain.report.entity.QReport.report;

@Repository
@RequiredArgsConstructor
public class ReportRepositoryCustomImpl implements ReportRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Report> findAdminReports(String status, String targetType, Pageable pageable) {
        List<Long> reportIds = queryFactory
                .select(report.reportId)
                .from(report)
                .where(statusEq(status), targetTypeEq(targetType))
                .orderBy(report.createdAt.desc(), report.reportId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(report.count())
                .from(report)
                .where(statusEq(status), targetTypeEq(targetType))
                .fetchOne();

        if (reportIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total != null ? total : 0L);
        }

        Map<Long, Integer> orderById = java.util.stream.IntStream.range(0, reportIds.size())
                .boxed()
                .collect(Collectors.toMap(reportIds::get, Function.identity()));

        List<Report> content = queryFactory
                .selectFrom(report)
                .join(report.reporter).fetchJoin()
                .leftJoin(report.admin).fetchJoin()
                .where(report.reportId.in(reportIds))
                .fetch()
                .stream()
                .sorted(Comparator.comparingInt(item -> orderById.getOrDefault(item.getReportId(), Integer.MAX_VALUE)))
                .toList();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression statusEq(String status) {
        return StringUtils.hasText(status) ? report.status.eq(status.trim()) : null;
    }

    private BooleanExpression targetTypeEq(String targetType) {
        return StringUtils.hasText(targetType) ? report.targetType.eq(targetType.trim()) : null;
    }
}
