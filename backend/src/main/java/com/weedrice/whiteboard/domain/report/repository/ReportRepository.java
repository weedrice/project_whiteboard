package com.weedrice.whiteboard.domain.report.repository;

import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long>, ReportRepositoryCustom {
    Optional<Report> findByReporterAndTargetTypeAndTargetId(User reporter, String targetType, Long targetId);

    long countByStatus(String status);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Page<Report> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Page<Report> findByTargetTypeOrderByCreatedAtDesc(String targetType, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Page<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(String targetType, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Page<Report> findByReporterOrderByCreatedAtDesc(User reporter, Pageable pageable);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    long countByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);
}
