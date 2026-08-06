package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.service.AdminEligibleUserService;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
class BoardCommandService {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final AdminEligibleUserService adminEligibleUserService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final ModerationAuditLogService moderationAuditLogService;
    private final DomainLockManager domainLockManager;

    BoardCreateCommandResult createBoard(Long creatorId, BoardCreateRequest request) {
        domainLockManager.lockBoardOrder();
        User creator = adminEligibleUserService.lockActiveUser(getCurrentUser(creatorId));
        String iconUrl = normalizeIconUrl(request.getIconUrl());
        String boardUrl = BoardUrlNormalizer.normalizeCreatable(request.getBoardUrl());

        Integer maxSortOrder = boardRepository.findMaxSortOrder();

        Board board = Board.builder()
                .boardName(request.getBoardName())
                .boardUrl(boardUrl)
                .description(normalizeDescription(request.getDescription()))
                .creator(creator)
                .iconUrl(iconUrl)
                .sortOrder(maxSortOrder + 1)
                .isPublic(request.getIsPublic())
                .isListed(resolveCreateListed(request.getIsPublic(), request.getIsListed()))
                .agentUseYn(resolveAgentUseYn(request.getIsPublic(), request.getAgentUseYn()))
                .build();

        Board savedBoard;
        try {
            savedBoard = boardRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException ex) {
            throw resolveBoardConflict(ex);
        }
        return new BoardCreateCommandResult(savedBoard, creator, creatorId, request.getGuidePrompt());
    }

    BoardUpdateCommandResult updateBoard(String boardUrl, BoardUpdateRequest request, Long userId) {
        if (request.getSortOrder() != null) {
            domainLockManager.lockBoardOrder();
        }
        String currentBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrlForUpdate(currentBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUser(userId);
        String previousIconUrl = board.getIconUrl();
        String previousBoardName = board.getBoardName();
        Boolean previousIsActive = board.getIsActive();
        Boolean previousIsPublic = board.getIsPublic();
        String iconUrl = normalizeIconUrl(request.getIconUrl());

        boardAccessPolicy.validateBoardAdmin(board, currentUser);

        String requestedBoardUrl = BoardUrlNormalizer.normalizeOptionalCreatable(request.getBoardUrl());
        if (requestedBoardUrl != null && !Objects.equals(board.getBoardUrl(), requestedBoardUrl)) {
            board.updateBoardUrl(requestedBoardUrl);
        }

        Integer sortOrder = request.getSortOrder() != null ? request.getSortOrder() : board.getSortOrder();
        board.update(request.getBoardName(), normalizeDescription(request.getDescription()), iconUrl,
                sortOrder,
                request.getAllowNsfw() != null ? request.getAllowNsfw() : board.getAllowNsfw(),
                request.getIsActive(),
                request.getIsPublic(),
                resolveUpdateListed(board, request),
                resolveAgentUseYn(
                        request.getIsPublic() != null ? request.getIsPublic() : board.getIsPublic(),
                        request.getAgentUseYn()));
        try {
            boardRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException ex) {
            throw resolveBoardConflict(ex);
        }
        if (Boolean.TRUE.equals(previousIsActive) && Boolean.FALSE.equals(board.getIsActive())) {
            if (request.getModerationReason() == null || request.getModerationReason().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            moderationAuditLogService.recordUserAction(currentUser,
                    ModerationAuditLogService.ACTION_BOARD_DEACTIVATE,
                    ModerationAuditLogService.TARGET_TYPE_BOARD,
                    board.getBoardId(), board, request.getModerationReason());
        }
        return new BoardUpdateCommandResult(board, currentUser, previousIconUrl, previousBoardName,
                previousIsActive, previousIsPublic,
                request.getGuidePrompt());
    }

    private User getCurrentUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Boolean resolveAgentUseYn(Boolean isPublic, Boolean requestedAgentUseYn) {
        if (Boolean.FALSE.equals(isPublic)) {
            return false;
        }
        return requestedAgentUseYn;
    }

    private Boolean resolveCreateListed(Boolean isPublic, Boolean requestedIsListed) {
        return !Boolean.FALSE.equals(isPublic) && !Boolean.FALSE.equals(requestedIsListed);
    }

    private Boolean resolveUpdateListed(Board board, BoardUpdateRequest request) {
        if (Boolean.FALSE.equals(request.getIsPublic())) {
            return false;
        }
        if (request.getIsListed() != null) {
            return request.getIsListed();
        }
        if (Boolean.TRUE.equals(request.getIsPublic())) {
            return true;
        }
        return board.getIsListed();
    }

    private BusinessException resolveBoardConflict(DataIntegrityViolationException ex) {
        if (ConstraintNameMatcher.containsBoardUrlConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_URL);
        }
        if (ConstraintNameMatcher.containsBoardNameConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
        }
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? "" : description;
    }

    private String normalizeIconUrl(String iconUrl) {
        String normalizedIconUrl = TextInputNormalizer.normalizeNullable(iconUrl);
        return normalizedIconUrl == null || normalizedIconUrl.isEmpty() ? null : normalizedIconUrl;
    }
}
