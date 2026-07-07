package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SemanticSearchServiceTest {

    private SemanticSearchProperties properties;
    private EmbeddingClient embeddingClient;
    private SemanticSearchQueryTransactionService transactionService;
    private SemanticSearchService semanticSearchService;

    @BeforeEach
    void setUp() {
        properties = new SemanticSearchProperties();
        embeddingClient = mock(EmbeddingClient.class);
        transactionService = mock(SemanticSearchQueryTransactionService.class);
        semanticSearchService = new SemanticSearchService(
                properties,
                embeddingClient,
                transactionService,
                new SemanticSearchTextBuilder());
    }

    @Test
    void search_usesKeywordFallbackWhenDisabled() {
        when(transactionService.loadQueryContext(null, null))
                .thenReturn(new SemanticSearchQueryContext(null, null, false, List.of()));
        when(transactionService.searchKeyword(any(SemanticSearchKeywordQuery.class))).thenReturn(
                new SemanticSearchQueryRows(List.of(
                        new SemanticSearchRow("POST", 100L, 100L, 10L, "board", "Board", "hello",
                                "<p>semantic fallback</p>", null, "KEYWORD_FALLBACK", null,
                                1L, null, "USER", "author", null)), 1L));

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                " hello ", "POST", null, 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRankSource()).isEqualTo("KEYWORD_FALLBACK");
        assertThat(result.getContent().get(0).getExcerpt()).isEqualTo("semantic fallback");
        verifyNoInteractions(embeddingClient);
        verify(transactionService).searchKeyword(argThat(query ->
                query.contentType() == SemanticSearchContentType.POST
                        && query.keyword().equals("hello")
                        && query.limit() == 20
                        && query.offset() == 0));
    }

    @Test
    void search_usesVectorRepositoryWhenEnabled() {
        properties.setEnabled(true);
        when(transactionService.loadQueryContext(null, null))
                .thenReturn(new SemanticSearchQueryContext(null, null, false, List.of()));
        when(embeddingClient.isAvailable()).thenReturn(true);
        when(embeddingClient.embed("hello")).thenReturn(new float[1536]);
        when(transactionService.searchVector(any(SemanticSearchQuery.class))).thenReturn(
                new SemanticSearchQueryRows(List.of(
                        new SemanticSearchRow("POST", 100L, 100L, 10L, "board", "Board", "hello", "excerpt",
                                0.91, "VECTOR", null, 1L, null, "USER", "author", null)), 1L));

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                "hello", "ALL", null, 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRankSource()).isEqualTo("VECTOR");
        verify(transactionService, never()).searchKeyword(any());
    }

    @Test
    void search_usesKeywordFallbackWhenEmbeddingFails() {
        properties.setEnabled(true);
        when(transactionService.loadQueryContext(null, null))
                .thenReturn(new SemanticSearchQueryContext(null, null, false, List.of()));
        when(embeddingClient.isAvailable()).thenReturn(true);
        when(embeddingClient.embed("hello")).thenThrow(new IllegalStateException("provider down"));
        when(transactionService.searchKeyword(any(SemanticSearchKeywordQuery.class))).thenReturn(
                new SemanticSearchQueryRows(List.of(
                        new SemanticSearchRow("POST", 100L, 100L, 10L, "board", "Board", "hello",
                                "fallback excerpt", null, "KEYWORD_FALLBACK", null,
                                1L, null, "USER", "author", null)), 1L));

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                "hello", "ALL", null, 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRankSource()).isEqualTo("KEYWORD_FALLBACK");
        verify(transactionService, never()).searchVector(any());
        verify(transactionService).searchKeyword(any(SemanticSearchKeywordQuery.class));
    }

    @Test
    void search_propagatesVectorSearchFailureWithoutKeywordFallback() {
        properties.setEnabled(true);
        when(transactionService.loadQueryContext(null, null))
                .thenReturn(new SemanticSearchQueryContext(null, null, false, List.of()));
        when(embeddingClient.isAvailable()).thenReturn(true);
        when(embeddingClient.embed("hello")).thenReturn(new float[1536]);
        when(transactionService.searchVector(any(SemanticSearchQuery.class)))
                .thenThrow(new IllegalStateException("vector store down"));

        assertThatThrownBy(() -> semanticSearchService.search("hello", "ALL", null, 0, 20, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector store down");

        verify(transactionService).searchVector(any(SemanticSearchQuery.class));
        verify(transactionService, never()).searchKeyword(any());
    }

    @Test
    void search_usesScopedCommentFallbackForBoardUrlWhenSemanticDisabled() {
        SemanticSearchRow row = new SemanticSearchRow(
                "COMMENT",
                300L,
                200L,
                70L,
                "public-board",
                "Public Board",
                "post title",
                "<p>matched comment</p>",
                null,
                "KEYWORD_FALLBACK",
                LocalDateTime.of(2026, 5, 19, 12, 0),
                8L,
                null,
                "USER",
                "author",
                null);

        when(transactionService.loadQueryContext("public-board", 7L))
                .thenReturn(new SemanticSearchQueryContext("public-board", 7L, false, List.of(9L)));
        when(transactionService.searchKeyword(any(SemanticSearchKeywordQuery.class)))
                .thenReturn(new SemanticSearchQueryRows(List.of(row), 1L));

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                " comment ", "COMMENT", "public-board", 0, 20, 7L);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getExcerpt()).isEqualTo("matched comment");
        verify(transactionService).searchKeyword(argThat(query ->
                query.contentType() == SemanticSearchContentType.COMMENT
                        && query.boardUrl().equals("public-board")
                        && query.viewerUserId().equals(7L)
                        && query.blockedUserIds().equals(List.of(9L))));
    }

    @Test
    void search_usesUnionKeywordFallbackForAllContentTypes() {
        when(transactionService.loadQueryContext(null, null))
                .thenReturn(new SemanticSearchQueryContext(null, null, false, List.of()));
        when(transactionService.searchKeyword(any(SemanticSearchKeywordQuery.class))).thenReturn(
                new SemanticSearchQueryRows(List.of(
                        new SemanticSearchRow("COMMENT", 300L, 200L, 70L, "board", "Board", "post title",
                                "matched comment", null, "KEYWORD_FALLBACK",
                                LocalDateTime.of(2026, 5, 19, 12, 0), 8L, null, "USER", "author", null),
                        new SemanticSearchRow("POST", 100L, 100L, 10L, "board", "Board", "hello",
                                "matched post", null, "KEYWORD_FALLBACK",
                                LocalDateTime.of(2026, 5, 19, 11, 0), 1L, null, "USER", "author", null)), 4L));

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                "match", "ALL", null, 1, 2, null);

        assertThat(result.getTotalElements()).isEqualTo(4L);
        assertThat(result.getContent()).extracting(SemanticSearchResultResponse::getContentType)
                .containsExactly("COMMENT", "POST");
        verify(transactionService).searchKeyword(argThat(query ->
                query.contentType() == SemanticSearchContentType.ALL
                        && query.limit() == 2
                        && query.offset() == 2));
    }

    @Test
    void serviceMethodsAreNotTransactional() throws NoSuchMethodException {
        assertThat(SemanticSearchService.class.getAnnotation(Transactional.class)).isNull();
        Method method = SemanticSearchService.class.getMethod(
                "search",
                String.class,
                String.class,
                String.class,
                int.class,
                int.class,
                Long.class);
        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }
}
