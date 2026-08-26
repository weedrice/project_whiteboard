package com.weedrice.whiteboard.domain.inquiry.repository;

import com.weedrice.whiteboard.domain.inquiry.entity.InquiryMessage;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryMessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {
    List<InquiryMessage> findByInquiryIdOrderByCreatedAtAscMessageIdAsc(Long inquiryId);
    List<InquiryMessage> findByInquiryIdAndMessageTypeInOrderByCreatedAtAscMessageIdAsc(
            Long inquiryId, Collection<InquiryMessageType> messageTypes);

    @Query("""
            SELECT message
            FROM InquiryMessage message
            WHERE message.inquiryId IN :inquiryIds
              AND message.messageType IN :messageTypes
              AND NOT EXISTS (
                  SELECT newer.messageId
                  FROM InquiryMessage newer
                  WHERE newer.inquiryId = message.inquiryId
                    AND newer.messageType IN :messageTypes
                    AND (newer.createdAt > message.createdAt
                         OR (newer.createdAt = message.createdAt AND newer.messageId > message.messageId))
              )
            """)
    List<InquiryMessage> findLatestByInquiryIdInAndMessageTypeIn(
            @Param("inquiryIds") Collection<Long> inquiryIds,
            @Param("messageTypes") Collection<InquiryMessageType> messageTypes);

    boolean existsByInquiryIdAndMessageType(Long inquiryId, InquiryMessageType messageType);
}
