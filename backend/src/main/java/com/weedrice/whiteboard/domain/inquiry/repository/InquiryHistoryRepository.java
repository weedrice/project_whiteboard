package com.weedrice.whiteboard.domain.inquiry.repository;

import com.weedrice.whiteboard.domain.inquiry.entity.InquiryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryHistoryRepository extends JpaRepository<InquiryHistory, Long> {
    List<InquiryHistory> findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(Long inquiryId);
}
