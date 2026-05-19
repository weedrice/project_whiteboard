package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.search.service.SearchRequestNormalizer;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SemanticSearchService {

    private final SemanticSearchProperties properties;
    private final EmbeddingClient embeddingClient;
    private final SemanticSearchVectorRepository vectorRepository;
    private final SemanticSearchKeywordFallbackRepository keywordFallbackRepository;
    private final BoardRepository boardRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final SemanticSearchTextBuilder textBuilder;

    public Page<SemanticSearchResultResponse> search(String keyword, String contentType, String boardUrl,
            int page, int size, Long currentUserId) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword);
        SemanticSearchContentType normalizedContentType = SemanticSearchContentType.from(contentType);
        Pageable pageable = PageRequestUtils.of(page, size);
        User viewer = resolveViewer(currentUserId);
        validateBoardAccess(boardUrl, viewer);
        List<Long> blockedUserIds = currentUserId == null
                ? List.of()
                : userBlockService.getBlockedUserIdsEitherDirection(currentUserId);

        if (properties.isEnabled() && embeddingClient.isAvailable()) {
            try {
                float[] queryEmbedding = embeddingClient.embed(canonicalKeyword);
                SemanticSearchQuery query = new SemanticSearchQuery(
                        normalizedContentType,
                        normalizeBoardUrl(boardUrl),
                        currentUserId,
                        viewer != null && viewer.isUsableSuperAdmin(),
                        blockedUserIds,
                        SemanticSearchVectorFormatter.format(queryEmbedding),
                        pageable.getPageSize(),
                        pageable.getOffset());
                List<SemanticSearchResultResponse> results = vectorRepository.search(query)
                        .stream()
                        .map(SemanticSearchRow::toResponse)
                        .toList();
                return new PageImpl<>(results, pageable, vectorRepository.count(query));
            } catch (RuntimeException ex) {
                log.warn("Semantic vector search failed. Falling back to keyword search. contentType={}, boardUrl={}",
                        normalizedContentType, boardUrl, ex);
            }
        }
        return fallbackKeywordSearch(canonicalKeyword, normalizedContentType, normalizeBoardUrl(boardUrl),
                blockedUserIds, viewer, pageable);
    }

    private User resolveViewer(Long currentUserId) {
        return currentUserId == null ? null : userRepository.findById(currentUserId).orElse(null);
    }

    private void validateBoardAccess(String boardUrl, User viewer) {
        String normalizedBoardUrl = normalizeBoardUrl(boardUrl);
        if (normalizedBoardUrl == null) {
            return;
        }
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        if (!boardAccessPolicy.canReadBoard(board, viewer)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    private Page<SemanticSearchResultResponse> fallbackKeywordSearch(String keyword,
            SemanticSearchContentType contentType, String boardUrl, List<Long> blockedUserIds,
            User viewer, Pageable pageable) {
        Long currentUserId = viewer != null ? viewer.getUserId() : null;
        SemanticSearchKeywordQuery query = new SemanticSearchKeywordQuery(
                contentType,
                keyword,
                boardUrl,
                currentUserId,
                viewer != null && viewer.isUsableSuperAdmin(),
                blockedUserIds,
                pageable.getPageSize(),
                pageable.getOffset());
        List<SemanticSearchResultResponse> results = keywordFallbackRepository.search(query)
                .stream()
                .map(this::toKeywordFallbackResponse)
                .toList();
        return new PageImpl<>(results, pageable, keywordFallbackRepository.count(query));
    }

    private SemanticSearchResultResponse toKeywordFallbackResponse(SemanticSearchRow row) {
        return SemanticSearchResultResponse.builder()
                .contentType(row.contentType())
                .contentId(row.contentId())
                .postId(row.postId())
                .boardId(row.boardId())
                .boardUrl(row.boardUrl())
                .boardName(row.boardName())
                .title(row.title())
                .excerpt(textBuilder.excerpt(row.excerpt()))
                .similarity(row.similarity())
                .rankSource(row.rankSource())
                .createdAt(row.createdAt())
                .author(SemanticSearchResultResponse.Author.builder()
                        .userId(row.authorUserId())
                        .agentId(row.authorAgentId())
                        .authorType(row.authorType())
                        .displayName(row.authorDisplayName())
                        .profileImageUrl(row.authorProfileImageUrl())
                        .build())
                .build();
    }

    private String normalizeBoardUrl(String boardUrl) {
        return boardUrl == null || boardUrl.isBlank() ? null : boardUrl.trim();
    }
}
