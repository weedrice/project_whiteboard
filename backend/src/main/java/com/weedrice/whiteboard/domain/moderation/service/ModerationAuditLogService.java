package com.weedrice.whiteboard.domain.moderation.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.moderation.dto.ModerationAuditLogResponse;
import com.weedrice.whiteboard.domain.moderation.dto.ModerationAuditLogSearchCondition;
import com.weedrice.whiteboard.domain.moderation.entity.ModerationAuditLog;
import com.weedrice.whiteboard.domain.moderation.repository.ModerationAuditLogRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModerationAuditLogService {

    public static final String ACTION_POST_PIN = "POST_PIN";
    public static final String ACTION_POST_UNPIN = "POST_UNPIN";
    public static final String ACTION_POST_BLIND = "POST_BLIND";
    public static final String ACTION_POST_UNBLIND = "POST_UNBLIND";
    public static final String ACTION_POST_AUTO_BLIND = "POST_AUTO_BLIND";
    public static final String ACTION_COMMENT_AUTO_BLIND = "COMMENT_AUTO_BLIND";
    public static final String TARGET_TYPE_POST = "POST";
    public static final String TARGET_TYPE_COMMENT = "COMMENT";

    private final ModerationAuditLogRepository moderationAuditLogRepository;
    private final BoardRepository boardRepository;
    private final UserReadableResolver userReadableResolver;
    private final BoardAccessPolicy boardAccessPolicy;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUserAction(
            User actor,
            String action,
            String targetType,
            Long targetId,
            Board board,
            String reason) {
        moderationAuditLogRepository.save(ModerationAuditLog.builder()
                .actorType(ModerationAuditLog.ACTOR_TYPE_USER)
                .actorUser(actor)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .board(board)
                .reason(normalizeReason(reason))
                .build());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystemAction(
            String action,
            String targetType,
            Long targetId,
            Board board,
            String reason) {
        moderationAuditLogRepository.save(ModerationAuditLog.builder()
                .actorType(ModerationAuditLog.ACTOR_TYPE_SYSTEM)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .board(board)
                .reason(normalizeReason(reason))
                .build());
    }

    public Page<ModerationAuditLogResponse> getAdminAudits(
            String action,
            String actorType,
            Long boardId,
            String boardUrl,
            Long actorUserId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        ModerationAuditLogSearchCondition condition = ModerationAuditLogSearchCondition.builder()
                .action(normalizeFilter(action))
                .actorType(normalizeFilter(actorType))
                .boardId(boardId)
                .boardUrl(normalizeFilter(boardUrl))
                .actorUserId(actorUserId)
                .createdFrom(toStartOfDay(startDate))
                .createdTo(toExclusiveEnd(endDate))
                .build();
        validateDateRange(condition.createdFrom(), condition.createdTo());
        return moderationAuditLogRepository.search(condition, pageable)
                .map(ModerationAuditLogResponse::from);
    }

    public Page<ModerationAuditLogResponse> getBoardManagerAudits(
            Long managerUserId,
            String boardUrl,
            String action,
            String actorType,
            Long actorUserId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        User manager = userReadableResolver.resolve(managerUserId);
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        boardAccessPolicy.validateBoardAdmin(board, manager);
        ModerationAuditLogSearchCondition condition = ModerationAuditLogSearchCondition.builder()
                .action(normalizeFilter(action))
                .actorType(normalizeFilter(actorType))
                .boardId(board.getBoardId())
                .actorUserId(actorUserId)
                .createdFrom(toStartOfDay(startDate))
                .createdTo(toExclusiveEnd(endDate))
                .build();
        validateDateRange(condition.createdFrom(), condition.createdTo());
        return moderationAuditLogRepository.search(condition, pageable)
                .map(ModerationAuditLogResponse::from);
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime toExclusiveEnd(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    private void validateDateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
