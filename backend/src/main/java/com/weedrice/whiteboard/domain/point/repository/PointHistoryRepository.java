package com.weedrice.whiteboard.domain.point.repository;

import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
    List<PointHistory> findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
            User user,
            String type,
            String relatedType,
            Long relatedId);
}
