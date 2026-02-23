package com.weedrice.whiteboard.global.log.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.entity.ErrorLog;
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
    public Page<ErrorLog> searchErrorLogs(ErrorLogSearchRequest condition, Pageable pageable) {
        QErrorLog errorLog = QErrorLog.errorLog;

        BooleanExpression predicate = buildPredicate(errorLog, condition);

        List<ErrorLog> content = queryFactory
                .selectFrom(errorLog)
                .where(predicate)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(errorLog.createdAt.desc())
                .fetch();

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
        if (condition.getErrorType() != null && !condition.getErrorType().trim().isEmpty()) {
            predicate = combineAnd(predicate, errorLog.errorType.containsIgnoreCase(condition.getErrorType()));
        }

        // 에러 코드 필터
        if (condition.getErrorCode() != null && !condition.getErrorCode().trim().isEmpty()) {
            predicate = combineAnd(predicate, errorLog.errorCode.eq(condition.getErrorCode()));
        }

        // HTTP 상태 코드 필터
        if (condition.getHttpStatus() != null) {
            predicate = combineAnd(predicate, errorLog.httpStatus.eq(condition.getHttpStatus()));
        }

        // 확인 여부 필터
        if (condition.getIsResolved() != null && !condition.getIsResolved().trim().isEmpty()) {
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
        if (condition.getRequestUri() != null && !condition.getRequestUri().trim().isEmpty()) {
            predicate = combineAnd(predicate, errorLog.requestUri.containsIgnoreCase(condition.getRequestUri()));
        }

        return predicate;
    }

    private BooleanExpression combineAnd(BooleanExpression base, BooleanExpression condition) {
        return base == null ? condition : base.and(condition);
    }
}
