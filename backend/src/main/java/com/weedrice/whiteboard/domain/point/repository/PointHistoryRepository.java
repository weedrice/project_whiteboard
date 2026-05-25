package com.weedrice.whiteboard.domain.point.repository;

import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
    Page<PointHistory> findByUser_UserIdOrderByCreatedAtDescHistoryIdDesc(Long userId, Pageable pageable);
    Page<PointHistory> findByUser_UserIdAndTypeOrderByCreatedAtDescHistoryIdDesc(
            Long userId,
            String type,
            Pageable pageable);
    boolean existsByUser_UserIdAndTypeAndRelatedTypeAndRelatedId(
            Long userId,
            String type,
            String relatedType,
            Long relatedId);
    @Query("""
            SELECT COALESCE(SUM(ph.amount), 0)
            FROM PointHistory ph
            WHERE ph.user = :user
              AND ph.type = :type
              AND ph.relatedType = :relatedType
              AND ph.relatedId = :relatedId
              AND ph.amount > 0
            """)
    long sumPositiveAmountByUserAndTypeAndRelatedTypeAndRelatedId(
            @Param("user") User user,
            @Param("type") String type,
            @Param("relatedType") String relatedType,
            @Param("relatedId") Long relatedId);
}
