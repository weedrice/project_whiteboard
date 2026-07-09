package com.weedrice.whiteboard.domain.report.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.report.entity.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.weedrice.whiteboard.domain.comment.entity.QComment.comment;
import static com.weedrice.whiteboard.domain.post.entity.QPost.post;
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
                .orderBy(toOrderSpecifiers(pageable.getSort()))
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

    @Override
    public Page<Report> findBoardReports(Long boardId, String status, String targetType, Pageable pageable) {
        BooleanExpression boardScope = boardTargetEq(boardId);
        List<Long> reportIds = queryFactory
                .select(report.reportId)
                .from(report)
                .where(statusEq(status), targetTypeEq(targetType), boardScope)
                .orderBy(toOrderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(report.count())
                .from(report)
                .where(statusEq(status), targetTypeEq(targetType), boardScope)
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

    private BooleanExpression boardTargetEq(Long boardId) {
        if (boardId == null) {
            return null;
        }
        BooleanExpression reportedPostInBoard = report.targetType.eq("POST")
                .and(report.targetId.in(
                        com.querydsl.jpa.JPAExpressions.select(post.postId)
                                .from(post)
                                .where(post.board.boardId.eq(boardId))));
        BooleanExpression reportedCommentInBoard = report.targetType.eq("COMMENT")
                .and(report.targetId.in(
                        com.querydsl.jpa.JPAExpressions.select(comment.commentId)
                                .from(comment)
                                .where(comment.post.board.boardId.eq(boardId))));
        return reportedPostInBoard.or(reportedCommentInBoard);
    }

    private BooleanExpression statusEq(String status) {
        return StringUtils.hasText(status) ? report.status.eq(status.trim()) : null;
    }

    private BooleanExpression targetTypeEq(String targetType) {
        return StringUtils.hasText(targetType) ? report.targetType.eq(targetType.trim()) : null;
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return new OrderSpecifier<?>[] {report.createdAt.desc(), report.reportId.desc()};
        }
        return sort.stream()
                .map(this::toOrderSpecifier)
                .toArray(OrderSpecifier[]::new);
    }

    private OrderSpecifier<?> toOrderSpecifier(Sort.Order order) {
        ComparableExpressionBase<?> path = switch (order.getProperty()) {
            case "reportId" -> report.reportId;
            case "status" -> report.status;
            case "targetType" -> report.targetType;
            case "targetId" -> report.targetId;
            case "reasonType" -> report.reasonType;
            case "createdAt" -> report.createdAt;
            default -> report.createdAt;
        };
        return new OrderSpecifier<>(order.isAscending() ? Order.ASC : Order.DESC, path);
    }
}
