package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticSearchQueryTransactionServiceTest {

    private SemanticSearchQueryTransactionService transactionService;
    private SemanticSearchVectorRepository vectors;
    private SemanticSearchKeywordFallbackRepository keywords;
    private BoardRepository boardRepository;
    private UserRepository userRepository;
    private UserBlockService userBlockService;

    @BeforeEach
    void setUp() {
        boardRepository = mock(BoardRepository.class);
        userRepository = mock(UserRepository.class);
        userBlockService = mock(UserBlockService.class);
        vectors = mock(SemanticSearchVectorRepository.class);
        keywords = mock(SemanticSearchKeywordFallbackRepository.class);
        transactionService = new SemanticSearchQueryTransactionService(
                vectors,
                keywords,
                boardRepository,
                new BoardAccessPolicy(mock(AdminRepository.class)),
                userRepository,
                userBlockService);
    }

    @Test
    void queryStepsDeclareReadOnlyTransactions() throws NoSuchMethodException {
        assertThat(transactionalAnnotation("loadQueryContext", String.class, Long.class).readOnly()).isTrue();
        assertThat(transactionalAnnotation("searchVector", SemanticSearchQuery.class).readOnly()).isTrue();
        assertThat(transactionalAnnotation("searchKeyword", SemanticSearchKeywordQuery.class).readOnly()).isTrue();
    }

    @Test
    void hybridFetchesEachFixedPoolOnceAndPagesAfterDedupWithoutCounts() throws NoSuchMethodException {
        var vectorQuery = new SemanticSearchQuery(SemanticSearchContentType.ALL, "board", List.of(9L), "[1,0]", 1, 1, 0.3, 20);
        var keywordQuery = new SemanticSearchKeywordQuery(SemanticSearchContentType.ALL, "needle", "board", List.of(9L), 1, 1, 20);
        var indexed = SemanticSearchHybridRankingTest.row("POST", 1, "other", "VECTOR", 0.9);
        var unindexed = SemanticSearchHybridRankingTest.row("POST", 2, "needle", "KEYWORD_FALLBACK", null);
        when(vectors.search(vectorQuery.candidates())).thenReturn(List.of(indexed));
        when(keywords.search(keywordQuery.candidates())).thenReturn(List.of(unindexed));

        var result = transactionService.searchHybrid(vectorQuery, keywordQuery);

        assertThat(result.results()).containsExactly(indexed);
        assertThat(result.totalElements()).isEqualTo(2);
        verify(vectors).configureCandidateSearch(20);
        verify(vectors).search(vectorQuery.candidates());
        verify(keywords).search(keywordQuery.candidates());
        org.mockito.Mockito.verifyNoMoreInteractions(vectors, keywords);
        var transaction = transactionalAnnotation("searchHybrid", SemanticSearchQuery.class, SemanticSearchKeywordQuery.class);
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(org.springframework.transaction.annotation.Isolation.REPEATABLE_READ);
    }

    @Test
    void filteredVectorPoolCanBeEmptyWhileKeywordCandidatesStillFillResults() {
        var vectorQuery = new SemanticSearchQuery(SemanticSearchContentType.POST, "board", List.of(9L), "[1,0]", 10, 0);
        var keywordQuery = new SemanticSearchKeywordQuery(SemanticSearchContentType.POST, "needle", "board", List.of(9L), 10, 0);
        var unindexed = SemanticSearchHybridRankingTest.row("POST", 2, "needle", "KEYWORD_FALLBACK", null);
        when(vectors.search(vectorQuery.candidates())).thenReturn(List.of());
        when(keywords.search(keywordQuery.candidates())).thenReturn(List.of(unindexed));

        var result = transactionService.searchHybrid(vectorQuery, keywordQuery);

        assertThat(result.results()).containsExactly(unindexed);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(vectors).configureCandidateSearch(200);
        verify(vectors).search(vectorQuery.candidates());
        verify(keywords).search(keywordQuery.candidates());
        org.mockito.Mockito.verifyNoMoreInteractions(vectors, keywords);
    }

    @Test
    void keywordFallbackFetchesFixedPoolAndCalculatesTotalFromReturnedRows() {
        var query = new SemanticSearchKeywordQuery(SemanticSearchContentType.POST, "needle", "board", List.of(), 1, 1, 20);
        var first = SemanticSearchHybridRankingTest.row("POST", 1, "needle", "KEYWORD_FALLBACK", null);
        var second = SemanticSearchHybridRankingTest.row("POST", 2, "needle", "KEYWORD_FALLBACK", null);
        when(keywords.search(query.candidates())).thenReturn(List.of(first, second));

        var result = transactionService.searchKeyword(query);

        assertThat(result.results()).containsExactly(second);
        assertThat(result.totalElements()).isEqualTo(2);
        verify(keywords).search(query.candidates());
        org.mockito.Mockito.verifyNoMoreInteractions(keywords);
        org.mockito.Mockito.verifyNoInteractions(vectors);
    }

    @Test
    void loadQueryContext_throwsWhenCurrentUserIdIsInvalid() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.loadQueryContext(null, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userBlockService, never()).getBlockedUserIdsEitherDirectionForExistingUser(99L);
    }

    @Test
    void loadQueryContext_usesExistingUserBlockedLookup() {
        User viewer = User.builder()
                .loginId("viewer")
                .password("password")
                .email("viewer@test.com")
                .displayName("Viewer")
                .build();
        ReflectionTestUtils.setField(viewer, "userId", 7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(7L)).thenReturn(List.of(8L));

        SemanticSearchQueryContext context = transactionService.loadQueryContext(null, 7L);

        assertThat(context.blockedUserIds()).containsExactly(8L);
        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(7L);
        verify(userBlockService, never()).getBlockedUserIdsEitherDirection(7L);
    }

    @Test
    void loadQueryContext_preservesBoardNotFoundPrecedenceForInvalidUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(boardRepository.findByBoardUrl("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.loadQueryContext("missing", 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(userBlockService, never()).getBlockedUserIdsEitherDirectionForExistingUser(99L);
    }

    @Test
    void loadQueryContext_invalidUserAfterReadableBoardThrowsUserNotFound() {
        Board publicBoard = Board.builder()
                .boardName("Public")
                .boardUrl("free")
                .creator(User.builder().loginId("owner").build())
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(publicBoard, "isActive", true);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(publicBoard));

        assertThatThrownBy(() -> transactionService.loadQueryContext("free", 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userBlockService, never()).getBlockedUserIdsEitherDirectionForExistingUser(99L);
    }

    @Test
    void loadQueryContext_rejectsPrivateBoardScopeEvenForSuperAdmin() {
        User viewer = User.builder()
                .loginId("admin")
                .password("password")
                .email("admin@test.com")
                .displayName("Admin")
                .build();
        ReflectionTestUtils.setField(viewer, "userId", 7L);
        viewer.grantSuperAdminRole();
        Board privateBoard = Board.builder()
                .boardName("Private")
                .boardUrl("private")
                .creator(viewer)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(privateBoard, "isActive", true);

        when(userRepository.findById(7L)).thenReturn(Optional.of(viewer));
        when(boardRepository.findByBoardUrl("private")).thenReturn(Optional.of(privateBoard));

        assertThatThrownBy(() -> transactionService.loadQueryContext(" private ", 7L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(userBlockService, never()).getBlockedUserIdsEitherDirectionForExistingUser(7L);
    }

    @Test
    void loadQueryContext_rejectsInactivePublicBoardScopeEvenForSuperAdmin() {
        User viewer = User.builder()
                .loginId("admin")
                .password("password")
                .email("admin@test.com")
                .displayName("Admin")
                .build();
        ReflectionTestUtils.setField(viewer, "userId", 7L);
        viewer.grantSuperAdminRole();
        Board inactiveBoard = Board.builder()
                .boardName("Inactive")
                .boardUrl("inactive")
                .creator(viewer)
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(inactiveBoard, "isActive", false);

        when(userRepository.findById(7L)).thenReturn(Optional.of(viewer));
        when(boardRepository.findByBoardUrl("inactive")).thenReturn(Optional.of(inactiveBoard));

        assertThatThrownBy(() -> transactionService.loadQueryContext("inactive", 7L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(userBlockService, never()).getBlockedUserIdsEitherDirectionForExistingUser(7L);
    }

    @Test
    void loadQueryContext_rejectsInquiryBoardScope() {
        Board inquiryBoard = Board.builder()
                .boardName("Inquiry")
                .boardUrl("inquiry")
                .creator(User.builder().loginId("owner").build())
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(inquiryBoard, "isActive", true);

        when(boardRepository.findByBoardUrl("inquiry")).thenReturn(Optional.of(inquiryBoard));

        assertThatThrownBy(() -> transactionService.loadQueryContext("inquiry", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
    }

    private Transactional transactionalAnnotation(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = transactionService.getClass().getMethod(methodName, parameterTypes);
        return method.getAnnotation(Transactional.class);
    }
}
