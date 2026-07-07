package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
class SemanticSearchQueryTransactionService {

    private final SemanticSearchVectorRepository vectorRepository;
    private final SemanticSearchKeywordFallbackRepository keywordFallbackRepository;
    private final BoardRepository boardRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;

    @Transactional(readOnly = true)
    public SemanticSearchQueryContext loadQueryContext(String boardUrl, Long currentUserId) {
        String normalizedBoardUrl = normalizeBoardUrl(boardUrl);
        User viewer = resolveViewerForBoardAccess(currentUserId);
        validateBoardAccess(normalizedBoardUrl, viewer);
        viewer = requireViewerIfRequested(currentUserId, viewer);
        List<Long> blockedUserIds = currentUserId == null
                ? List.of()
                : userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(currentUserId);
        return new SemanticSearchQueryContext(
                normalizedBoardUrl,
                viewer != null ? viewer.getUserId() : null,
                viewer != null && viewer.isUsableSuperAdmin(),
                blockedUserIds);
    }

    @Transactional(readOnly = true)
    public SemanticSearchQueryRows searchVector(SemanticSearchQuery query) {
        return new SemanticSearchQueryRows(vectorRepository.search(query), vectorRepository.count(query));
    }

    @Transactional(readOnly = true)
    public SemanticSearchQueryRows searchKeyword(SemanticSearchKeywordQuery query) {
        return new SemanticSearchQueryRows(keywordFallbackRepository.search(query), keywordFallbackRepository.count(query));
    }

    private void validateBoardAccess(String normalizedBoardUrl, User viewer) {
        if (normalizedBoardUrl == null) {
            return;
        }
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (!boardAccessPolicy.canReadBoard(board, viewer)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    private String normalizeBoardUrl(String boardUrl) {
        return boardUrl == null || boardUrl.isBlank() ? null : boardUrl.trim();
    }

    private User resolveViewerForBoardAccess(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userRepository.findById(currentUserId).orElse(null);
    }

    private User requireViewerIfRequested(Long currentUserId, User viewer) {
        if (currentUserId == null) {
            return null;
        }
        if (viewer == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return viewer;
    }
}
