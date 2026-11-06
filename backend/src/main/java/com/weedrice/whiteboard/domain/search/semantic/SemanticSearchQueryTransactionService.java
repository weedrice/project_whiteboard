package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
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
        requireViewerIfRequested(currentUserId, viewer);
        List<Long> blockedUserIds = currentUserId == null
                ? List.of()
                : userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(currentUserId);
        return new SemanticSearchQueryContext(
                normalizedBoardUrl,
                blockedUserIds);
    }

    @Transactional(readOnly = true)
    public SemanticSearchQueryRows searchVector(SemanticSearchQuery query) {
        vectorRepository.configureCandidateSearch(query.candidateLimit());
        return SemanticSearchHybridRanking.page(vectorRepository.search(query.candidates()),
                query.candidateLimit(), query.limit(), query.offset());
    }

    @Transactional(readOnly = true)
    public SemanticSearchQueryRows searchKeyword(SemanticSearchKeywordQuery query) {
        return SemanticSearchHybridRanking.page(keywordFallbackRepository.search(query.candidates()),
                query.candidateLimit(), query.limit(), query.offset());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SemanticSearchQueryRows searchHybrid(SemanticSearchQuery vectors, SemanticSearchKeywordQuery keywords) {
        vectorRepository.configureCandidateSearch(vectors.candidateLimit());
        return SemanticSearchHybridRanking.merge(
                vectorRepository.search(vectors.candidates()), keywordFallbackRepository.search(keywords.candidates()),
                keywords.keyword(), vectors.candidateLimit(), vectors.limit(), vectors.offset());
    }

    @Transactional(readOnly = true)
    public SemanticRelatedPostResult findRelatedPostIds(
            Long postId,
            SemanticSearchQueryContext context,
            int size,
            double minSimilarity) {
        return vectorRepository.findRelatedPostIds(postId, context, size, minSimilarity);
    }

    private void validateBoardAccess(String normalizedBoardUrl, User viewer) {
        if (normalizedBoardUrl == null) {
            return;
        }
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (!isIndexedBoard(board)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
        if (!boardAccessPolicy.canReadBoard(board, viewer)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    private String normalizeBoardUrl(String boardUrl) {
        if (boardUrl == null || boardUrl.isBlank()) {
            return null;
        }
        return BoardUrlNormalizer.normalizeLookup(boardUrl);
    }

    private boolean isIndexedBoard(Board board) {
        return Boolean.TRUE.equals(board.getIsActive())
                && Boolean.TRUE.equals(board.getIsPublic())
                && !boardAccessPolicy.isInquiryBoard(board);
    }

    private User resolveViewerForBoardAccess(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userRepository.findById(currentUserId).orElse(null);
    }

    private void requireViewerIfRequested(Long currentUserId, User viewer) {
        if (currentUserId == null) {
            return;
        }
        if (viewer == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
