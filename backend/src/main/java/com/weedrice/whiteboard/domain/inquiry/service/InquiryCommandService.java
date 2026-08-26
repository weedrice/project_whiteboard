package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.dto.*;
import com.weedrice.whiteboard.domain.inquiry.entity.*;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryFilePort;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryUserPort;
import com.weedrice.whiteboard.domain.inquiry.repository.*;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.SuperAdminPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InquiryCommandService {
    private static final int MAX_ACTIVE_INQUIRIES = 5;
    private static final int MAX_IMAGES_PER_MESSAGE = 5;
    private static final EnumSet<InquiryStatus> ACTIVE_STATUSES = EnumSet.of(
            InquiryStatus.NEW, InquiryStatus.IN_PROGRESS, InquiryStatus.RESOLVED);

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository messageRepository;
    private final InquiryHistoryRepository historyRepository;
    private final InquiryUserPort userPort;
    private final InquiryFilePort filePort;
    private final InquiryReadService readService;
    private final InquiryNotificationPort notificationPort;
    private final SuperAdminPolicy superAdminPolicy;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Transactional
    public InquiryDetailResponse create(Long userId, InquiryCreateRequest request) {
        Long authorUserId = userPort.lockActiveUserId(userId);
        long activeCount = inquiryRepository.countByAuthorUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (activeCount >= MAX_ACTIVE_INQUIRIES) {
            throw new BusinessException(ErrorCode.INQUIRY_ACTIVE_LIMIT_EXCEEDED);
        }
        LocalDateTime now = now();
        Inquiry inquiry = inquiryRepository.save(new Inquiry(
                authorUserId, request.category(), normalize(request.title(), 200), now));
        InquiryMessage message = saveMessage(inquiry, userId, InquiryMessageType.USER_MESSAGE,
                request.content(), request.fileIds(), now);
        historyRepository.save(new InquiryHistory(inquiry.getInquiryId(), userId, InquiryHistoryAction.CREATED,
                null, InquiryStatus.NEW, null, now));
        meterRegistry.counter("noviis.inquiry.created").increment();
        notificationPort.notifySuperAdmins(userId, inquiry.getInquiryId(), "notification.inquiry.created");
        return readService.getMineDetail(userId, inquiry.getInquiryId());
    }

    @Transactional
    public InquiryDetailResponse addUserMessage(Long userId, Long inquiryId, InquiryMessageCreateRequest request) {
        Inquiry inquiry = getOwnedForUpdate(inquiryId, userId);
        LocalDateTime now = now();
        InquiryStatus previous = inquiry.addUserMessage(now);
        saveMessage(inquiry, userId, InquiryMessageType.USER_MESSAGE, request.content(), request.fileIds(), now);
        if (previous == InquiryStatus.RESOLVED) {
            historyRepository.save(new InquiryHistory(inquiryId, userId, InquiryHistoryAction.REOPENED_BY_USER,
                    previous, InquiryStatus.NEW, null, now));
            meterRegistry.counter("noviis.inquiry.reopened", "actor", "user").increment();
            notificationPort.notifySuperAdmins(userId, inquiryId, "notification.inquiry.reopened");
        }
        return readService.getMineDetail(userId, inquiryId);
    }

    @Transactional
    public InquiryDetailResponse withdraw(Long userId, Long inquiryId) {
        Inquiry inquiry = getOwnedForUpdate(inquiryId, userId);
        if (messageRepository.existsByInquiryIdAndMessageType(inquiryId, InquiryMessageType.STAFF_REPLY)) {
            throw new BusinessException(ErrorCode.INVALID_INQUIRY_STATE);
        }
        InquiryStatus previous = inquiry.getStatus();
        inquiry.withdraw(userId, now());
        saveHistory(inquiry, userId, InquiryHistoryAction.WITHDRAWN, previous, null);
        meterRegistry.counter("noviis.inquiry.closed", "reason", "withdrawn").increment();
        return readService.getMineDetail(userId, inquiryId);
    }

    @Transactional
    public InquiryDetailResponse closeByUser(Long userId, Long inquiryId) {
        Inquiry inquiry = getOwnedForUpdate(inquiryId, userId);
        InquiryStatus previous = inquiry.getStatus();
        inquiry.closeByUser(userId, now());
        saveHistory(inquiry, userId, InquiryHistoryAction.CLOSED_BY_USER, previous, null);
        meterRegistry.counter("noviis.inquiry.closed", "reason", "user").increment();
        return readService.getMineDetail(userId, inquiryId);
    }

    @Transactional
    public InquiryDetailResponse start(Long adminUserId, Long inquiryId) {
        requireAdmin(adminUserId);
        Inquiry inquiry = getForUpdate(inquiryId);
        InquiryStatus previous = inquiry.getStatus();
        inquiry.start();
        saveHistory(inquiry, adminUserId, InquiryHistoryAction.STARTED, previous, null);
        return readService.getAdminDetail(inquiryId);
    }

    @Transactional
    public InquiryDetailResponse reply(Long adminUserId, Long inquiryId, InquiryMessageCreateRequest request) {
        requireAdmin(adminUserId);
        Inquiry inquiry = getForUpdate(inquiryId);
        LocalDateTime now = now();
        InquiryStatus previous = inquiry.reply(now);
        saveMessage(inquiry, adminUserId, InquiryMessageType.STAFF_REPLY,
                request.content(), request.fileIds(), now);
        historyRepository.save(new InquiryHistory(inquiryId, adminUserId, InquiryHistoryAction.REPLIED,
                previous, InquiryStatus.RESOLVED, null, now));
        meterRegistry.counter("noviis.inquiry.replied").increment();
        notificationPort.notifyAuthor(adminUserId, inquiry.getAuthorUserId(), inquiryId,
                "notification.inquiry.replied");
        return readService.getAdminDetail(inquiryId);
    }

    @Transactional
    public InquiryDetailResponse addInternalNote(Long adminUserId, Long inquiryId,
                                                  InquiryMessageCreateRequest request) {
        requireAdmin(adminUserId);
        Inquiry inquiry = getForUpdate(inquiryId);
        saveMessage(inquiry, adminUserId, InquiryMessageType.INTERNAL_NOTE,
                request.content(), request.fileIds(), now());
        return readService.getAdminDetail(inquiryId);
    }

    @Transactional
    public InquiryDetailResponse closeByAdmin(Long adminUserId, Long inquiryId, AdminInquiryCloseRequest request) {
        requireAdmin(adminUserId);
        Inquiry inquiry = getForUpdate(inquiryId);
        InquiryStatus previous = inquiry.closeByAdmin(adminUserId, now());
        saveHistory(inquiry, adminUserId, InquiryHistoryAction.CLOSED_BY_ADMIN,
                previous, normalize(request.reason(), 500));
        meterRegistry.counter("noviis.inquiry.closed", "reason", "admin").increment();
        notificationPort.notifyAuthor(adminUserId, inquiry.getAuthorUserId(), inquiryId,
                "notification.inquiry.closed");
        return readService.getAdminDetail(inquiryId);
    }

    @Transactional
    public InquiryDetailResponse reopenByAdmin(Long adminUserId, Long inquiryId) {
        requireAdmin(adminUserId);
        Inquiry snapshot = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        Long authorUserId = userPort.lockUserId(snapshot.getAuthorUserId());
        Inquiry inquiry = getForUpdate(inquiryId);
        if (!inquiry.isOwnedBy(authorUserId)) {
            throw new BusinessException(ErrorCode.INQUIRY_NOT_FOUND);
        }
        if (inquiry.getStatus() != InquiryStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_INQUIRY_STATE);
        }
        long activeCount = inquiryRepository.countByAuthorUserIdAndStatusIn(authorUserId, ACTIVE_STATUSES);
        if (activeCount >= MAX_ACTIVE_INQUIRIES) {
            throw new BusinessException(ErrorCode.INQUIRY_ACTIVE_LIMIT_EXCEEDED);
        }
        InquiryStatus previous = inquiry.reopenByAdmin(now());
        saveHistory(inquiry, adminUserId, InquiryHistoryAction.REOPENED_BY_ADMIN, previous, null);
        meterRegistry.counter("noviis.inquiry.reopened", "actor", "admin").increment();
        return readService.getAdminDetail(inquiryId);
    }

    private InquiryMessage saveMessage(Inquiry inquiry, Long authorUserId, InquiryMessageType type,
                                       String content, List<Long> fileIds, LocalDateTime now) {
        InquiryMessage message = messageRepository.save(new InquiryMessage(
                inquiry.getInquiryId(), authorUserId, type, normalize(content, 10_000), now));
        filePort.associateMessageFiles(
                safeFiles(fileIds), authorUserId, message.getMessageId(), MAX_IMAGES_PER_MESSAGE);
        return message;
    }

    private List<Long> safeFiles(List<Long> fileIds) {
        return fileIds == null ? List.of() : List.copyOf(fileIds);
    }

    private void saveHistory(Inquiry inquiry, Long actorUserId, InquiryHistoryAction action,
                             InquiryStatus previous, String reason) {
        historyRepository.save(new InquiryHistory(inquiry.getInquiryId(), actorUserId, action,
                previous, inquiry.getStatus(), reason, now()));
    }

    private Inquiry getOwnedForUpdate(Long inquiryId, Long userId) {
        Inquiry inquiry = inquiryRepository.findByIdForCommand(inquiryId).orElse(null);
        if (inquiry == null || !inquiry.isOwnedBy(userId)) {
            meterRegistry.counter("noviis.inquiry.access_denied").increment();
            throw new BusinessException(ErrorCode.INQUIRY_NOT_FOUND);
        }
        return inquiry;
    }

    private Inquiry getForUpdate(Long inquiryId) {
        return inquiryRepository.findByIdForCommand(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
    }

    private void requireAdmin(Long adminUserId) {
        superAdminPolicy.requireUsableSuperAdmin(adminUserId);
    }

    private String normalize(String value, int maxLength) {
        if (value == null) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
