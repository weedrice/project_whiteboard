package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

final class InquirySpecifications {
    private InquirySpecifications() {}

    static Specification<Inquiry> adminFilters(
            InquiryStatus status,
            InquiryCategory category,
            InquiryPriority priority,
            String keyword,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime highCutoff,
            LocalDateTime urgentCutoff,
            LocalDateTime highCategoryUrgentCutoff) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            if (category != null) predicate = cb.and(predicate, cb.equal(root.get("category"), category));
            if (from != null) predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicate = cb.and(predicate, cb.lessThan(root.get("createdAt"), to));
            if (keyword != null && !keyword.isBlank()) {
                String escaped = keyword.strip().toLowerCase().replace("\\", "\\\\")
                        .replace("%", "\\%").replace("_", "\\_");
                predicate = cb.and(predicate,
                        cb.like(cb.lower(root.get("title")), "%" + escaped + "%", '\\'));
            }
            if (priority != null) {
                var active = root.get("status").in(InquiryStatus.NEW, InquiryStatus.IN_PROGRESS);
                var highCategory = root.get("category").in(InquiryCategory.ACCOUNT, InquiryCategory.TECHNICAL);
                var staffSince = root.<LocalDateTime>get("staffActionSince");
                var urgent = cb.or(
                        cb.and(highCategory, cb.lessThanOrEqualTo(staffSince, highCategoryUrgentCutoff)),
                        cb.and(cb.not(highCategory), cb.lessThanOrEqualTo(staffSince, urgentCutoff)));
                var high = cb.or(
                        cb.and(highCategory, cb.greaterThan(staffSince, highCategoryUrgentCutoff)),
                        cb.and(cb.not(highCategory), cb.lessThanOrEqualTo(staffSince, highCutoff),
                                cb.greaterThan(staffSince, urgentCutoff)));
                var normal = cb.and(cb.not(highCategory), cb.greaterThan(staffSince, highCutoff));
                predicate = cb.and(predicate, active, switch (priority) {
                    case URGENT -> urgent;
                    case HIGH -> high;
                    case NORMAL -> normal;
                });
            }
            if (query != null && query.getResultType() != Long.class && query.getOrderList().isEmpty()) {
                var active = root.get("status").in(InquiryStatus.NEW, InquiryStatus.IN_PROGRESS);
                var highCategory = root.get("category").in(InquiryCategory.ACCOUNT, InquiryCategory.TECHNICAL);
                var staffSince = root.<LocalDateTime>get("staffActionSince");
                var urgent = cb.or(
                        cb.and(highCategory, cb.lessThanOrEqualTo(staffSince, highCategoryUrgentCutoff)),
                        cb.and(cb.not(highCategory), cb.lessThanOrEqualTo(staffSince, urgentCutoff)));
                var high = cb.or(
                        cb.and(highCategory, cb.greaterThan(staffSince, highCategoryUrgentCutoff)),
                        cb.and(cb.not(highCategory), cb.lessThanOrEqualTo(staffSince, highCutoff),
                                cb.greaterThan(staffSince, urgentCutoff)));
                var activeRank = cb.selectCase().when(active, 0).otherwise(1);
                var priorityRank = cb.selectCase().when(urgent, 0).when(high, 1)
                        .when(active, 2).otherwise(3);
                query.orderBy(cb.asc(activeRank), cb.asc(priorityRank), cb.asc(staffSince),
                        cb.asc(root.get("createdAt")), cb.asc(root.get("inquiryId")));
            }
            return predicate;
        };
    }
}
