package com.weedrice.whiteboard.domain.report.repository;

import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long>, ReportRepositoryCustom {
    boolean existsByReporterAndTargetTypeAndTargetIdAndStatus(User reporter, String targetType, Long targetId,
            String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.reportId = :reportId")
    Optional<Report> findByIdForUpdate(@Param("reportId") Long reportId);

    long countByStatus(String status);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Page<Report> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Page<Report> findByTargetTypeOrderByCreatedAtDesc(String targetType, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Page<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(String targetType, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Page<Report> findByReporterOrderByCreatedAtDescReportIdDesc(User reporter, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "admin"})
    Optional<Report> findByReportId(Long reportId);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    long countByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);
}
