package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.service.AdminEligibleUserService;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
class BoardCommandService {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final AdminEligibleUserService adminEligibleUserService;
    private final BoardAccessPolicy boardAccessPolicy;

    BoardCommandService(BoardRepository boardRepository,
                        UserRepository userRepository,
                        AdminEligibleUserService adminEligibleUserService,
                        BoardAccessPolicy boardAccessPolicy) {
        this.boardRepository = boardRepository;
        this.userRepository = userRepository;
        this.adminEligibleUserService = adminEligibleUserService;
        this.boardAccessPolicy = boardAccessPolicy;
    }

    BoardCreateCommandResult createBoard(Long creatorId, BoardCreateRequest request) {
        User creator = getCurrentUser(creatorId);
        adminEligibleUserService.validateActiveUser(creator);
        String iconUrl = normalizeIconUrl(request.getIconUrl());

        validateCreatableBoardUrl(request.getBoardUrl());

        Integer maxSortOrder = boardRepository.findMaxSortOrder();

        Board board = Board.builder()
                .boardName(request.getBoardName())
                .boardUrl(request.getBoardUrl())
                .description(normalizeDescription(request.getDescription()))
                .creator(creator)
                .iconUrl(iconUrl)
                .sortOrder(maxSortOrder + 1)
                .isPublic(request.getIsPublic())
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
        Board board = boardRepository.findByBoardUrlForUpdate(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User currentUser = getCurrentUser(userId);
        String previousIconUrl = board.getIconUrl();
        String previousBoardName = board.getBoardName();
        String iconUrl = normalizeIconUrl(request.getIconUrl());

        boardAccessPolicy.validateBoardAdmin(board, currentUser);

        if (request.getBoardUrl() != null && !Objects.equals(board.getBoardUrl(), request.getBoardUrl())) {
            validateCreatableBoardUrl(request.getBoardUrl());
            board.updateBoardUrl(request.getBoardUrl());
        }

        Integer sortOrder = request.getSortOrder() != null ? request.getSortOrder() : board.getSortOrder();
        board.update(request.getBoardName(), normalizeDescription(request.getDescription()), iconUrl,
                sortOrder,
                request.getAllowNsfw() != null ? request.getAllowNsfw() : board.getAllowNsfw(),
                request.getIsActive(),
                request.getIsPublic(),
                resolveAgentUseYn(
                        request.getIsPublic() != null ? request.getIsPublic() : board.getIsPublic(),
                        request.getAgentUseYn()));
        try {
            boardRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException ex) {
            throw resolveBoardConflict(ex);
        }
        return new BoardUpdateCommandResult(board, currentUser, previousIconUrl, previousBoardName,
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

    private BusinessException resolveBoardConflict(DataIntegrityViolationException ex) {
        if (ConstraintNameMatcher.containsBoardUrlConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_URL);
        }
        if (ConstraintNameMatcher.containsBoardNameConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
        }
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    private void validateCreatableBoardUrl(String boardUrl) {
        if (boardUrl == null) {
            return;
        }
        if (BoardPolicyConstants.INQUIRY_BOARD_URL.equalsIgnoreCase(boardUrl.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reserved board URL");
        }
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? "" : description;
    }

    private String normalizeIconUrl(String iconUrl) {
        if (iconUrl == null) {
            return null;
        }
        String trimmedIconUrl = iconUrl.trim();
        return trimmedIconUrl.isEmpty() ? null : trimmedIconUrl;
    }
}
