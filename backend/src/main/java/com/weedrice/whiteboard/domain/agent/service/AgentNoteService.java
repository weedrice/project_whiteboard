package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteSendRequest;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentNote;
import com.weedrice.whiteboard.domain.agent.entity.AgentNoteThread;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteException;
import com.weedrice.whiteboard.domain.agent.repository.AgentNoteRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentNoteThreadRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.domain.message.constant.MessageConstraints;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentNoteService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_LIST_SIZE = 20;
    private static final int MAX_LIST_SIZE = 20;
    private static final int DEFAULT_THREAD_SIZE = 50;
    private static final int MAX_THREAD_SIZE = 50;
    private static final int PREVIEW_LENGTH = 120;
    private static final String ACTION_SEND_NOTE = "send_note";

    private final AgentRepository agentRepository;
    private final AgentNoteThreadRepository agentNoteThreadRepository;
    private final AgentNoteRepository agentNoteRepository;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentQuotaService agentQuotaService;
    private final AgentPolicyService agentPolicyService;
    private final AgentAuditService agentAuditService;
    private final UserBlockService userBlockService;

    public AgentNoteResponses.ThreadListResponse getNotes(Long agentId, String box, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Pageable safePageable = boundedPageable(pageable, DEFAULT_LIST_SIZE, MAX_LIST_SIZE);
        Page<Long> threadIds = findThreadIds(agent.getAgentId(), normalizeBox(box), safePageable);
        if (threadIds.isEmpty()) {
            return new AgentNoteResponses.ThreadListResponse(
                    new PageImpl<>(List.of(), safePageable, threadIds.getTotalElements()));
        }
        Map<Long, AgentNote> latestNotesByThreadId = agentNoteRepository.findLatestVisibleInThreads(
                        threadIds.getContent(),
                        agent.getAgentId())
                .stream()
                .collect(Collectors.toMap(note -> note.getThread().getNoteThreadId(), Function.identity()));
        Map<Long, Long> unreadCountsByThreadId = agentNoteRepository.countUnreadNotesByThreadIds(
                        threadIds.getContent(),
                        agent.getAgentId())
                .stream()
                .collect(Collectors.toMap(
                        AgentNoteRepository.ThreadUnreadCountProjection::getNoteThreadId,
                        AgentNoteRepository.ThreadUnreadCountProjection::getUnreadCount));
        List<AgentNoteResponses.ThreadListItem> items = threadIds.getContent()
                .stream()
                .map(threadId -> toThreadListItem(
                        agent.getAgentId(),
                        threadId,
                        latestNotesByThreadId.get(threadId),
                        unreadCountsByThreadId.getOrDefault(threadId, 0L)))
                .flatMap(List::stream)
                .toList();
        return new AgentNoteResponses.ThreadListResponse(
                new PageImpl<>(items, safePageable, threadIds.getTotalElements()));
    }

    public AgentNoteResponses.ThreadResponse getThread(Long agentId, Long noteThreadId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        AgentNoteThread thread = resolveParticipatingThread(agent.getAgentId(), noteThreadId);
        Pageable safePageable = boundedPageable(pageable, DEFAULT_THREAD_SIZE, MAX_THREAD_SIZE);
        Page<AgentNote> notes = agentNoteRepository.findVisibleThreadNotes(noteThreadId, agent.getAgentId(), safePageable);
        Page<AgentNoteResponses.ThreadNote> responsePage = notes.map(this::toThreadNote);
        return new AgentNoteResponses.ThreadResponse(
                noteThreadId,
                toAgentInfo(thread.counterpartOf(agent.getAgentId())),
                responsePage);
    }

    @Transactional
    public AgentNoteResponses.SendResponse sendNote(Long agentId, AgentNoteSendRequest request,
            AgentRequestContext requestContext) {
        Agent sender = agentOwnershipService.resolveActiveAgentForUpdate(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(sender);
        validateCanSendNote(policy);

        String recipientName = normalizeRecipientName(request.getRecipientAgentName());
        Agent recipient = agentRepository.findByNameAndIsDeletedFalse(recipientName)
                .orElseThrow(() -> writeException(
                        AgentWriteErrorCode.NOTE_RECIPIENT_NOT_FOUND,
                        ACTION_SEND_NOTE,
                        policy,
                        null,
                        null));
        validateRecipient(sender, recipient, policy);
        validateNotBlocked(sender.getUser(), recipient.getUser(), policy);
        String content = normalizeContent(request.getContent(), policy);
        reserveNoteSend(sender, policy);

        AgentNoteThread thread = getOrCreateThread(sender, recipient);
        AgentNote note = agentNoteRepository.save(new AgentNote(thread, sender, recipient, content));
        agentAuditService.saveLog(
                sender,
                sender.getUser(),
                AgentAuditActionType.SEND_NOTE,
                AgentAuditTargetType.NOTE,
                note.getNoteId(),
                requestContext);

        return AgentNoteResponses.SendResponse.builder()
                .status("sent")
                .noteThreadId(thread.getNoteThreadId())
                .noteId(note.getNoteId())
                .sentAt(toOffsetDateTime(note.getCreatedAt(), LocalDateTime.now(KST)))
                .build();
    }

    @Transactional
    public AgentNoteResponses.ReadResponse markRead(Long agentId, Long noteThreadId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        resolveParticipatingThread(agent.getAgentId(), noteThreadId);
        int updatedCount = agentNoteRepository.markThreadReceivedNotesRead(noteThreadId, agent.getAgentId());
        if (updatedCount > 0) {
            agentAuditService.saveLog(
                    agent,
                    agent.getUser(),
                    AgentAuditActionType.MARK_NOTE_READ,
                    AgentAuditTargetType.NOTE,
                    noteThreadId,
                    requestContext);
        }
        return AgentNoteResponses.ReadResponse.builder()
                .noteThreadId(noteThreadId)
                .markedRead(true)
                .remainingUnreadCount(agentNoteRepository.countUnreadNotesInThread(noteThreadId, agent.getAgentId()))
                .build();
    }

    public AgentNoteResponses.Summary getSummary(Long agentId) {
        return AgentNoteResponses.Summary.builder()
                .unreadThreadCount(agentNoteRepository.countUnreadThreads(agentId))
                .unreadNoteCount(agentNoteRepository.countUnreadNotes(agentId))
                .build();
    }

    private Page<Long> findThreadIds(Long agentId, String box, Pageable pageable) {
        return switch (box) {
            case "sent" -> agentNoteThreadRepository.findSentThreadIds(agentId, pageable);
            case "unread" -> agentNoteThreadRepository.findUnreadThreadIds(agentId, pageable);
            default -> agentNoteThreadRepository.findInboxThreadIds(agentId, pageable);
        };
    }

    private String normalizeBox(String box) {
        if (box == null || box.isBlank()) {
            return "inbox";
        }
        String normalized = box.trim().toLowerCase(Locale.ROOT);
        if (!List.of("inbox", "sent", "unread").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private List<AgentNoteResponses.ThreadListItem> toThreadListItem(
            Long agentId,
            Long threadId,
            AgentNote latest,
            long unreadCount) {
        if (latest == null) {
            return List.of();
        }
        Agent counterpart = latest.getSenderAgent().getAgentId().equals(agentId)
                ? latest.getReceiverAgent()
                : latest.getSenderAgent();
        return List.of(AgentNoteResponses.ThreadListItem.builder()
                .noteThreadId(threadId)
                .latestNoteId(latest.getNoteId())
                .counterpartAgent(toAgentInfo(counterpart))
                .preview(toPreview(latest.getContent()))
                .unreadCount(unreadCount)
                .latestAt(toOffsetDateTime(latest.getCreatedAt(), null))
                .build());
    }

    private AgentNoteResponses.ThreadNote toThreadNote(AgentNote note) {
        return AgentNoteResponses.ThreadNote.builder()
                .noteId(note.getNoteId())
                .senderAgentName(note.getSenderAgent().getName())
                .content(note.getContent())
                .createdAt(toOffsetDateTime(note.getCreatedAt(), null))
                .read(Boolean.TRUE.equals(note.getIsRead()))
                .build();
    }

    private AgentNoteResponses.AgentInfo toAgentInfo(Agent agent) {
        if (agent == null) {
            return null;
        }
        return AgentNoteResponses.AgentInfo.builder()
                .name(agent.getName())
                .displayName(agent.getName())
                .build();
    }

    private AgentNoteThread resolveParticipatingThread(Long agentId, Long noteThreadId) {
        AgentNoteThread thread = agentNoteThreadRepository.findByIdWithParticipants(noteThreadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!thread.hasParticipant(agentId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return thread;
    }

    private AgentNoteThread getOrCreateThread(Agent firstAgent, Agent secondAgent) {
        Long lowAgentId = Math.min(firstAgent.getAgentId(), secondAgent.getAgentId());
        Long highAgentId = Math.max(firstAgent.getAgentId(), secondAgent.getAgentId());
        return agentNoteThreadRepository.findByAgentPairForUpdate(lowAgentId, highAgentId)
                .orElseGet(() -> {
                    agentNoteThreadRepository.insertIgnorePair(lowAgentId, highAgentId);
                    return agentNoteThreadRepository.findByAgentPairForUpdate(lowAgentId, highAgentId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
                });
    }

    private void validateCanSendNote(AgentPolicySnapshot policy) {
        if (policy.restrictions().isSuspended()) {
            throw writeException(
                    AgentWriteErrorCode.AGENT_SUSPENDED,
                    ACTION_SEND_NOTE,
                    policy,
                    null,
                    policy.restrictions().getSuspendedUntil());
        }
        if (!policy.restrictions().isCanSendNote()) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_SEND_FORBIDDEN,
                    ACTION_SEND_NOTE,
                    policy,
                    null,
                    null);
        }
        if (policy.limits().getNotesRemaining() <= 0) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_DAILY_LIMIT_EXCEEDED,
                    ACTION_SEND_NOTE,
                    policy,
                    policy.dailyStatus().resetAt(),
                    null);
        }
    }

    private void validateRecipient(Agent sender, Agent recipient, AgentPolicySnapshot policy) {
        if (Objects.equals(sender.getAgentId(), recipient.getAgentId())) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_SELF_SEND_FORBIDDEN,
                    ACTION_SEND_NOTE,
                    policy,
                    null,
                    null);
        }
        if (!recipient.isActive() || recipient.getUser() == null || !recipient.getUser().isActiveAccount()) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_SEND_FORBIDDEN,
                    ACTION_SEND_NOTE,
                    policy,
                    null,
                    null);
        }
    }

    private void validateNotBlocked(User sender, User recipient, AgentPolicySnapshot policy) {
        if (userBlockService.isEitherDirectionBlocked(sender.getUserId(), recipient.getUserId())) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_SEND_FORBIDDEN,
                    ACTION_SEND_NOTE,
                    policy,
                    null,
                    null);
        }
    }

    private String normalizeRecipientName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return value.trim();
    }

    private String normalizeContent(String value, AgentPolicySnapshot policy) {
        String normalized = value == null ? null : InputSanitizer.stripHtml(value).trim();
        if (normalized == null || normalized.isBlank() || normalized.length() > MessageConstraints.MAX_CONTENT_LENGTH) {
            throw writeException(
                    AgentWriteErrorCode.VALIDATION_FAILED,
                    ACTION_SEND_NOTE,
                    policy,
                    null,
                    null);
        }
        return normalized;
    }

    private void reserveNoteSend(Agent agent, AgentPolicySnapshot policy) {
        try {
            agentQuotaService.reserveNoteSend(agent);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.RATE_LIMIT_EXCEEDED) {
                throw writeException(
                        AgentWriteErrorCode.NOTE_DAILY_LIMIT_EXCEEDED,
                        e.getMessage(),
                        ACTION_SEND_NOTE,
                        policy,
                        policy.dailyStatus().resetAt(),
                        null);
            }
            throw e;
        }
    }

    private Pageable boundedPageable(Pageable pageable, int defaultSize, int maxSize) {
        int pageNumber = pageable != null && pageable.isPaged() ? Math.max(pageable.getPageNumber(), 0) : 0;
        int requestedSize = pageable != null && pageable.isPaged() ? pageable.getPageSize() : defaultSize;
        int pageSize = Math.min(Math.max(requestedSize, 1), maxSize);
        return PageRequest.of(pageNumber, pageSize);
    }

    private String toPreview(String content) {
        String plain = content == null ? "" : InputSanitizer.stripHtml(content).replaceAll("\\s+", " ").trim();
        return plain.length() <= PREVIEW_LENGTH ? plain : plain.substring(0, PREVIEW_LENGTH);
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value, LocalDateTime fallback) {
        LocalDateTime effective = value != null ? value : fallback;
        return effective == null ? null : effective.atZone(KST).toOffsetDateTime();
    }

    private AgentWriteException writeException(AgentWriteErrorCode errorCode, String action,
            AgentPolicySnapshot policy, OffsetDateTime resetAt, OffsetDateTime nextAllowedAt) {
        return writeException(errorCode, null, action, policy, resetAt, nextAllowedAt);
    }

    private AgentWriteException writeException(AgentWriteErrorCode errorCode, String message, String action,
            AgentPolicySnapshot policy, OffsetDateTime resetAt, OffsetDateTime nextAllowedAt) {
        return new AgentWriteException(
                errorCode,
                message,
                action,
                policy.limits(),
                policy.restrictions(),
                resetAt,
                nextAllowedAt);
    }
}
