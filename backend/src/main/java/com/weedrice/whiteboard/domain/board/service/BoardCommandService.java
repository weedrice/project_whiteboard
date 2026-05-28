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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
class BoardCommandService {

    private static final String BOARD_NAME_CONSTRAINT = "uk_boards_board_name";
    private static final String BOARD_URL_CONSTRAINT = "uk_boards_board_url";
    private static final String LEGACY_BOARD_NAME_CONSTRAINT = "boards_board_name_key";
    private static final String LEGACY_BOARD_URL_CONSTRAINT = "boards_board_url_key";
    private static final String BOARD_NAME_COLUMN = "board_name";
    private static final String BOARD_URL_COLUMN = "board_url";

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
        if (containsBoardUrlConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_URL);
        }
        if (containsBoardNameConstraint(ex)) {
            return new BusinessException(ErrorCode.DUPLICATE_BOARD_NAME);
        }
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    private boolean containsBoardNameConstraint(Throwable throwable) {
        return containsConstraint(throwable, BOARD_NAME_CONSTRAINT, LEGACY_BOARD_NAME_CONSTRAINT, BOARD_NAME_COLUMN);
    }

    private boolean containsBoardUrlConstraint(Throwable throwable) {
        return containsConstraint(throwable, BOARD_URL_CONSTRAINT, LEGACY_BOARD_URL_CONSTRAINT, BOARD_URL_COLUMN);
    }

    private boolean containsConstraint(Throwable throwable, String... candidates) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";
            if (containsAny(message, candidates)) {
                return true;
            }
            if (current instanceof ConstraintViolationException constraintViolationException) {
                String constraintName = constraintViolationException.getConstraintName();
                if (containsAny(constraintName != null ? constraintName.toLowerCase() : "", candidates)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
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
