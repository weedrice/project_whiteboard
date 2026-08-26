package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.dto.*;
import com.weedrice.whiteboard.domain.inquiry.entity.*;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryFilePort;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryUserPort;
import com.weedrice.whiteboard.domain.inquiry.repository.*;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryReadService {
    private static final Set<String> USER_SORTS = Set.of("createdAt", "modifiedAt", "title", "status");
    private static final Sort USER_DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));
    private static final Sort ADMIN_DEFAULT_SORT = Sort.unsorted();

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository messageRepository;
    private final InquiryHistoryRepository historyRepository;
    private final InquiryUserPort userPort;
    private final InquiryFilePort filePort;
    private final InquiryPriorityPolicy priorityPolicy;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public Page<InquirySummaryResponse> getMine(Long userId, InquiryStatus status, InquiryCategory category,
                                                Pageable pageable) {
        Pageable safe = PageRequestUtils.bounded(pageable, 20, 100, USER_DEFAULT_SORT, USER_SORTS);
        var spec = (org.springframework.data.jpa.domain.Specification<Inquiry>)
                (root, query, cb) -> cb.equal(root.get("authorUserId"), userId);
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (category != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        return mapSummaryPage(inquiryRepository.findAll(spec, safe));
    }

    public InquiryDetailResponse getMineDetail(Long userId, Long inquiryId) {
        Inquiry inquiry = getOwned(inquiryId, userId);
        return toDetail(inquiry, false);
    }

    public Page<InquirySummaryResponse> getAdminPage(InquiryStatus status, InquiryCategory category,
                                                     InquiryPriority priority, String keyword,
                                                     LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Pageable safe = PageRequestUtils.bounded(pageable, 20, 100, ADMIN_DEFAULT_SORT,
                Set.of("createdAt", "modifiedAt", "staffActionSince", "title", "status", "category"));
        LocalDateTime now = LocalDateTime.now(clock);
        InquiryPriorityPolicy.Thresholds thresholds = priorityPolicy.thresholds();
        return mapSummaryPage(inquiryRepository.findAll(InquirySpecifications.adminFilters(
                status, category, priority, keyword, from, to,
                now.minusHours(thresholds.highHours()), now.minusHours(thresholds.urgentHours()),
                now.minusHours(thresholds.highCategoryUrgentHours())), safe));
    }

    public InquiryDetailResponse getAdminDetail(Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        return toDetail(inquiry, true);
    }

    public boolean canAccessMessageFile(Long messageId, Long viewerUserId) {
        InquiryMessage message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return denied();
        Inquiry inquiry = inquiryRepository.findById(message.getInquiryId()).orElse(null);
        if (inquiry == null || viewerUserId == null) return denied();
        if (inquiry.isOwnedBy(viewerUserId)) {
            if (message.getMessageType().isPublic()) return true;
            return denied();
        }
        boolean allowed = userPort.isUsableSuperAdmin(viewerUserId);
        if (!allowed) denied();
        return allowed;
    }

    Inquiry getOwned(Long inquiryId, Long userId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId).orElse(null);
        if (inquiry == null || !inquiry.isOwnedBy(userId)) {
            denied();
            throw new BusinessException(ErrorCode.INQUIRY_NOT_FOUND);
        }
        return inquiry;
    }

    private Page<InquirySummaryResponse> mapSummaryPage(Page<Inquiry> page) {
        List<Inquiry> inquiries = page.getContent();
        Map<Long, String> userNames = loadUserNames(inquiries.stream().map(Inquiry::getAuthorUserId).toList());
        Map<Long, String> publicSummaries = loadLastPublicSummaries(
                inquiries.stream().map(Inquiry::getInquiryId).toList());
        return page.map(inquiry -> new InquirySummaryResponse(
                inquiry.getInquiryId(), inquiry.getCategory(), inquiry.getTitle(), inquiry.getStatus(),
                priorityPolicy.resolve(inquiry), publicSummaries.getOrDefault(inquiry.getInquiryId(), ""),
                inquiry.getAuthorUserId(), userNames.getOrDefault(inquiry.getAuthorUserId(), "-"),
                inquiry.getStaffActionSince(), inquiry.getCreatedAt(), visibleModifiedAt(inquiry)));
    }

    private InquiryDetailResponse toDetail(Inquiry inquiry, boolean admin) {
        List<InquiryMessageType> visibleTypes = admin
                ? List.of(InquiryMessageType.values())
                : List.of(InquiryMessageType.USER_MESSAGE, InquiryMessageType.STAFF_REPLY);
        List<InquiryMessage> messages = messageRepository
                .findByInquiryIdAndMessageTypeInOrderByCreatedAtAscMessageIdAsc(inquiry.getInquiryId(), visibleTypes);
        Map<Long, String> names = loadUserNames(messages.stream().map(InquiryMessage::getAuthorUserId).toList());
        names.putAll(loadUserNames(List.of(inquiry.getAuthorUserId())));
        Map<Long, List<InquiryAttachmentResponse>> attachmentsByMessageId = loadAttachments(messages);
        List<InquiryMessageResponse> messageResponses = messages.stream()
                .map(message -> toMessage(
                        message,
                        names.getOrDefault(message.getAuthorUserId(), "-"),
                        attachmentsByMessageId.getOrDefault(message.getMessageId(), List.of())))
                .toList();
        List<InquiryHistoryResponse> histories = historyRepository
                .findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(inquiry.getInquiryId()).stream()
                .map(history -> new InquiryHistoryResponse(history.getHistoryId(), history.getActionType(),
                        history.getFromStatus(), history.getToStatus(), history.getCreatedAt()))
                .toList();
        boolean hasStaffReply = messageRepository.existsByInquiryIdAndMessageType(
                inquiry.getInquiryId(), InquiryMessageType.STAFF_REPLY);
        InquiryAllowedActions actions = admin ? new InquiryAllowedActions(false, false, false)
                : new InquiryAllowedActions(
                        inquiry.getStatus() != InquiryStatus.CLOSED,
                        inquiry.getStatus() == InquiryStatus.NEW && !hasStaffReply,
                        inquiry.getStatus() == InquiryStatus.RESOLVED);
        return new InquiryDetailResponse(
                inquiry.getInquiryId(), inquiry.getAuthorUserId(),
                names.getOrDefault(inquiry.getAuthorUserId(), "-"), inquiry.getCategory(), inquiry.getTitle(),
                inquiry.getStatus(), priorityPolicy.resolve(inquiry),
                admin ? inquiry.getClosureReason() : safeClosureReason(inquiry.getClosureReason()),
                admin ? findAdminClosureReason(inquiry.getInquiryId()) : null,
                actions, messageResponses, histories, inquiry.getFirstRespondedAt(), inquiry.getResolvedAt(),
                inquiry.getClosedAt(), inquiry.getCreatedAt(), visibleModifiedAt(inquiry));
    }

    private InquiryMessageResponse toMessage(
            InquiryMessage message,
            String authorName,
            List<InquiryAttachmentResponse> attachments) {
        return new InquiryMessageResponse(message.getMessageId(), message.getAuthorUserId(), authorName,
                message.getMessageType(), message.getContent(), attachments, message.getCreatedAt());
    }

    private Map<Long, List<InquiryAttachmentResponse>> loadAttachments(List<InquiryMessage> messages) {
        List<Long> messageIds = messages.stream()
                .map(InquiryMessage::getMessageId)
                .filter(Objects::nonNull)
                .toList();
        if (messageIds.isEmpty()) return Map.of();
        return filePort.findMessageFiles(messageIds).stream()
                .sorted(Comparator.comparing(InquiryFilePort.MessageFile::fileId))
                .collect(Collectors.groupingBy(
                        InquiryFilePort.MessageFile::messageId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toAttachment, Collectors.toList())));
    }

    private InquiryAttachmentResponse toAttachment(InquiryFilePort.MessageFile file) {
        return new InquiryAttachmentResponse(file.fileId(), file.originalName(), file.fileSize(),
                file.mimeType(), file.url());
    }

    private Map<Long, String> loadUserNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) return names;
        Map<Long, String> resolved = userPort.findDisplayNames(ids);
        if (resolved != null) names.putAll(resolved);
        return names;
    }

    private Map<Long, String> loadLastPublicSummaries(List<Long> inquiryIds) {
        if (inquiryIds.isEmpty()) return Map.of();
        return messageRepository.findLatestByInquiryIdInAndMessageTypeIn(
                        inquiryIds,
                        List.of(InquiryMessageType.USER_MESSAGE, InquiryMessageType.STAFF_REPLY)).stream()
                .collect(Collectors.toMap(
                        InquiryMessage::getInquiryId,
                        message -> summarize(message.getContent()),
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    private String summarize(String content) {
        return content.length() <= 100 ? content : content.substring(0, 100) + "…";
    }

    private String findAdminClosureReason(Long inquiryId) {
        return historyRepository.findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(inquiryId).stream()
                .reduce((first, second) -> second).map(InquiryHistory::getReason).orElse(null);
    }

    private InquiryClosureReason safeClosureReason(InquiryClosureReason reason) {
        return reason == InquiryClosureReason.ADMIN_CLOSED ? null : reason;
    }

    private LocalDateTime visibleModifiedAt(Inquiry inquiry) {
        LocalDateTime entityModifiedAt = inquiry.getModifiedAt();
        LocalDateTime publicActivityAt = inquiry.getLastPublicActivityAt();
        if (entityModifiedAt == null) return publicActivityAt;
        if (publicActivityAt == null) return entityModifiedAt;
        return entityModifiedAt.isAfter(publicActivityAt) ? entityModifiedAt : publicActivityAt;
    }

    private boolean denied() {
        meterRegistry.counter("noviis.inquiry.access_denied").increment();
        return false;
    }
}
