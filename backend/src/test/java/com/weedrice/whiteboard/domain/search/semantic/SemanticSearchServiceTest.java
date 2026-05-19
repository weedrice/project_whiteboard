package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SemanticSearchServiceTest {

    private SemanticSearchProperties properties;
    private EmbeddingClient embeddingClient;
    private SemanticSearchVectorRepository vectorRepository;
    private SemanticSearchKeywordFallbackRepository keywordFallbackRepository;
    private BoardRepository boardRepository;
    private UserRepository userRepository;
    private UserBlockService userBlockService;
    private SemanticSearchService semanticSearchService;

    @BeforeEach
    void setUp() {
        properties = new SemanticSearchProperties();
        embeddingClient = mock(EmbeddingClient.class);
        vectorRepository = mock(SemanticSearchVectorRepository.class);
        keywordFallbackRepository = mock(SemanticSearchKeywordFallbackRepository.class);
        boardRepository = mock(BoardRepository.class);
        userRepository = mock(UserRepository.class);
        userBlockService = mock(UserBlockService.class);
        semanticSearchService = new SemanticSearchService(
                properties,
                embeddingClient,
                vectorRepository,
                keywordFallbackRepository,
                boardRepository,
                new BoardAccessPolicy(mock(AdminRepository.class)),
                userRepository,
                userBlockService,
                new SemanticSearchTextBuilder());
    }

    @Test
    void search_usesKeywordFallbackWhenDisabled() {
        when(keywordFallbackRepository.search(any(SemanticSearchKeywordQuery.class))).thenReturn(List.of(
                new SemanticSearchRow("POST", 100L, 100L, 10L, "board", "Board", "hello",
                        "<p>semantic fallback</p>", null, "KEYWORD_FALLBACK", null,
                        1L, null, "USER", "author", null)));
        when(keywordFallbackRepository.count(any(SemanticSearchKeywordQuery.class))).thenReturn(1L);

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                " hello ", "POST", null, 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRankSource()).isEqualTo("KEYWORD_FALLBACK");
        assertThat(result.getContent().get(0).getExcerpt()).isEqualTo("semantic fallback");
        verifyNoInteractions(embeddingClient, vectorRepository);
        verify(keywordFallbackRepository).search(argThat(query ->
                query.contentType() == SemanticSearchContentType.POST
                        && query.keyword().equals("hello")
                        && query.limit() == 20
                        && query.offset() == 0));
    }

    @Test
    void search_usesVectorRepositoryWhenEnabled() {
        properties.setEnabled(true);
        when(embeddingClient.isAvailable()).thenReturn(true);
        when(embeddingClient.embed("hello")).thenReturn(new float[1536]);
        when(vectorRepository.search(any(SemanticSearchQuery.class))).thenReturn(List.of(
                new SemanticSearchRow("POST", 100L, 100L, 10L, "board", "Board", "hello", "excerpt",
                        0.91, "VECTOR", null, 1L, null, "USER", "author", null)));
        when(vectorRepository.count(any(SemanticSearchQuery.class))).thenReturn(1L);

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                "hello", "ALL", null, 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRankSource()).isEqualTo("VECTOR");
        verifyNoInteractions(keywordFallbackRepository);
    }

    @Test
    void search_usesScopedCommentFallbackForBoardUrlWhenSemanticDisabled() {
        User viewer = User.builder().displayName("viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 7L);
        Board board = Board.builder()
                .boardName("Private Board")
                .boardUrl("private")
                .creator(viewer)
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 70L);
        SemanticSearchRow row = new SemanticSearchRow(
                "COMMENT",
                300L,
                200L,
                70L,
                "private",
                "Private Board",
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

        when(userRepository.findById(7L)).thenReturn(Optional.of(viewer));
        when(boardRepository.findByBoardUrl("private")).thenReturn(Optional.of(board));
        when(userBlockService.getBlockedUserIdsEitherDirection(7L)).thenReturn(List.of(9L));
        when(keywordFallbackRepository.search(any(SemanticSearchKeywordQuery.class)))
                .thenReturn(List.of(row));
        when(keywordFallbackRepository.count(any(SemanticSearchKeywordQuery.class)))
                .thenReturn(1L);

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                " comment ", "COMMENT", "private", 0, 20, 7L);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getExcerpt()).isEqualTo("matched comment");
        verify(keywordFallbackRepository).search(argThat(query ->
                query.contentType() == SemanticSearchContentType.COMMENT
                        && query.boardUrl().equals("private")
                        && query.viewerUserId().equals(7L)
                        && query.blockedUserIds().equals(List.of(9L))));
    }

    @Test
    void search_usesUnionKeywordFallbackForAllContentTypes() {
        when(keywordFallbackRepository.search(any(SemanticSearchKeywordQuery.class))).thenReturn(List.of(
                new SemanticSearchRow("COMMENT", 300L, 200L, 70L, "board", "Board", "post title",
                        "matched comment", null, "KEYWORD_FALLBACK",
                        LocalDateTime.of(2026, 5, 19, 12, 0), 8L, null, "USER", "author", null),
                new SemanticSearchRow("POST", 100L, 100L, 10L, "board", "Board", "hello",
                        "matched post", null, "KEYWORD_FALLBACK",
                        LocalDateTime.of(2026, 5, 19, 11, 0), 1L, null, "USER", "author", null)));
        when(keywordFallbackRepository.count(any(SemanticSearchKeywordQuery.class))).thenReturn(4L);

        Page<SemanticSearchResultResponse> result = semanticSearchService.search(
                "match", "ALL", null, 1, 2, null);

        assertThat(result.getTotalElements()).isEqualTo(4L);
        assertThat(result.getContent()).extracting(SemanticSearchResultResponse::getContentType)
                .containsExactly("COMMENT", "POST");
        verify(keywordFallbackRepository).search(argThat(query ->
                query.contentType() == SemanticSearchContentType.ALL
                        && query.limit() == 2
                        && query.offset() == 2));
    }
}
