package com.weedrice.whiteboard.domain.inquiry.repository;

import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long>, JpaSpecificationExecutor<Inquiry> {
    long countByAuthorUserIdAndStatusIn(Long authorUserId, Collection<InquiryStatus> statuses);

    Page<Inquiry> findByAuthorUserId(Long authorUserId, Pageable pageable);

    Page<Inquiry> findByAuthorUserIdAndStatus(Long authorUserId, InquiryStatus status, Pageable pageable);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT i FROM Inquiry i WHERE i.inquiryId = :inquiryId")
    Optional<Inquiry> findByIdForCommand(@Param("inquiryId") Long inquiryId);

    @Query("""
            SELECT i FROM Inquiry i
            WHERE i.status = com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus.RESOLVED
              AND i.resolvedAt <= :cutoff
            ORDER BY i.resolvedAt ASC, i.inquiryId ASC
            """)
    List<Inquiry> findAutoCloseCandidates(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
