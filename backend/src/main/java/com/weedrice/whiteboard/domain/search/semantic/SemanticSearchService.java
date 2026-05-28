package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.search.service.SearchRequestNormalizer;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final SemanticSearchProperties properties;
    private final EmbeddingClient embeddingClient;
    private final SemanticSearchQueryTransactionService transactionService;
    private final SemanticSearchTextBuilder textBuilder;

    public Page<SemanticSearchResultResponse> search(String keyword, String contentType, String boardUrl,
            int page, int size, Long currentUserId) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword);
        SemanticSearchContentType normalizedContentType = SemanticSearchContentType.from(contentType);
        Pageable pageable = PageRequestUtils.of(page, size);
        SemanticSearchQueryContext context = transactionService.loadQueryContext(boardUrl, currentUserId);

        if (properties.isEnabled() && embeddingClient.isAvailable()) {
            float[] queryEmbedding = embedOrFallback(canonicalKeyword, normalizedContentType, boardUrl);
            if (queryEmbedding != null) {
                SemanticSearchQuery query = new SemanticSearchQuery(
                        normalizedContentType,
                        context.boardUrl(),
                        context.viewerUserId(),
                        context.viewerSuperAdmin(),
                        context.blockedUserIds(),
                        SemanticSearchVectorFormatter.format(queryEmbedding),
                        pageable.getPageSize(),
                        pageable.getOffset());
                SemanticSearchQueryRows rows = transactionService.searchVector(query);
                List<SemanticSearchResultResponse> results = rows.results()
                        .stream()
                        .map(SemanticSearchRow::toResponse)
                        .toList();
                return new PageImpl<>(results, pageable, rows.totalElements());
            }
        }
        return fallbackKeywordSearch(canonicalKeyword, normalizedContentType, context, pageable);
    }

    private float[] embedOrFallback(String keyword, SemanticSearchContentType contentType, String boardUrl) {
        try {
            return embeddingClient.embed(keyword);
        } catch (RuntimeException ex) {
            log.warn("Semantic embedding failed. Falling back to keyword search. contentType={}, boardUrl={}",
                    contentType, boardUrl, ex);
            return null;
        }
    }

    private Page<SemanticSearchResultResponse> fallbackKeywordSearch(String keyword,
            SemanticSearchContentType contentType, SemanticSearchQueryContext context, Pageable pageable) {
        SemanticSearchKeywordQuery query = new SemanticSearchKeywordQuery(
                contentType,
                keyword,
                context.boardUrl(),
                context.viewerUserId(),
                context.viewerSuperAdmin(),
                context.blockedUserIds(),
                pageable.getPageSize(),
                pageable.getOffset());
        SemanticSearchQueryRows rows = transactionService.searchKeyword(query);
        List<SemanticSearchResultResponse> results = rows.results()
                .stream()
                .map(this::toKeywordFallbackResponse)
                .toList();
        return new PageImpl<>(results, pageable, rows.totalElements());
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

}
