package com.weedrice.whiteboard.global.log.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.dto.ErrorLogResponse;
import com.weedrice.whiteboard.global.log.entity.QErrorLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class ErrorLogRepositoryCustomImpl implements ErrorLogRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ErrorLogResponse.ErrorLogSummary> searchErrorLogs(ErrorLogSearchRequest condition, Pageable pageable) {
        QErrorLog errorLog = QErrorLog.errorLog;

        BooleanExpression predicate = buildPredicate(errorLog, condition);

        List<Tuple> rows = queryFactory
                .select(
                        errorLog.errorLogId,
                        errorLog.errorCode,
                        errorLog.errorType,
                        errorLog.httpStatus,
                        errorLog.message,
                        errorLog.requestUri,
                        errorLog.requestMethod,
                        errorLog.userId,
                        errorLog.ipAddress,
                        errorLog.userAgent,
                        errorLog.isResolved,
                        errorLog.resolvedBy,
                        errorLog.resolvedAt,
                        errorLog.resolvedMemo,
                        errorLog.createdAt)
                .from(errorLog)
                .where(predicate)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(errorLog.createdAt.desc(), errorLog.errorLogId.desc())
                .fetch();
        List<ErrorLogResponse.ErrorLogSummary> content = rows.stream()
                .map(row -> ErrorLogResponse.ErrorLogSummary.builder()
                        .errorLogId(row.get(errorLog.errorLogId))
                        .errorCode(row.get(errorLog.errorCode))
                        .errorType(row.get(errorLog.errorType))
                        .httpStatus(row.get(errorLog.httpStatus))
                        .message(row.get(errorLog.message))
                        .requestUri(row.get(errorLog.requestUri))
                        .requestMethod(row.get(errorLog.requestMethod))
                        .userId(row.get(errorLog.userId))
                        .ipAddress(row.get(errorLog.ipAddress))
                        .userAgent(row.get(errorLog.userAgent))
                        .isResolved(row.get(errorLog.isResolved))
                        .resolvedBy(row.get(errorLog.resolvedBy))
                        .resolvedAt(row.get(errorLog.resolvedAt))
                        .resolvedMemo(row.get(errorLog.resolvedMemo))
                        .createdAt(row.get(errorLog.createdAt))
                        .build())
                .toList();

        long total = queryFactory
                .select(errorLog.count())
                .from(errorLog)
                .where(predicate)
                .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }

    private BooleanExpression buildPredicate(QErrorLog errorLog, ErrorLogSearchRequest condition) {
        BooleanExpression predicate = null;

        if (condition == null) {
            return null;
        }

        // 에러 타입 필터
        if (hasText(condition.getErrorType())) {
            predicate = combineAnd(predicate, errorLog.errorType.containsIgnoreCase(condition.getErrorType()));
        }

        // 에러 코드 필터
        if (hasText(condition.getErrorCode())) {
            predicate = combineAnd(predicate, errorLog.errorCode.eq(condition.getErrorCode()));
        }

        // HTTP 상태 코드 필터
        if (condition.getHttpStatus() != null) {
            predicate = combineAnd(predicate, errorLog.httpStatus.eq(condition.getHttpStatus()));
        }

        // 확인 여부 필터
        if (hasText(condition.getIsResolved())) {
            predicate = combineAnd(predicate, errorLog.isResolved.eq(condition.getIsResolved()));
        }

        // 시작일 필터
        if (condition.getStartDate() != null) {
            predicate = combineAnd(predicate, errorLog.createdAt.goe(condition.getStartDate().atStartOfDay()));
        }

        // 종료일 필터
        if (condition.getEndDate() != null) {
            predicate = combineAnd(predicate, errorLog.createdAt.lt(condition.getEndDate().plusDays(1).atStartOfDay()));
        }

        // 요청 URI 필터
        if (hasText(condition.getRequestUri())) {
            predicate = combineAnd(predicate, errorLog.requestUri.containsIgnoreCase(condition.getRequestUri()));
        }

        return predicate;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BooleanExpression combineAnd(BooleanExpression base, BooleanExpression condition) {
        return base == null ? condition : base.and(condition);
    }
}
