package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentSummaryAssembler;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostSummaryAssembler;
import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import com.weedrice.whiteboard.domain.search.repository.SearchStatisticRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SearchBoardAccessTest {
    enum Endpoint { POSTS, PREVIEW }

    private AdminRepository admins;
    private BoardRepository boards;
    private UserRepository users;
    private PostRepository posts;
    private GlobalConfigService config;
    private SearchService search;
    private SearchPreviewReadService preview;
    private User viewer;
    private Board board;

    @BeforeEach
    void setUp() {
        admins = mock(AdminRepository.class);
        boards = mock(BoardRepository.class);
        users = mock(UserRepository.class);
        posts = mock(PostRepository.class);
        config = mock(GlobalConfigService.class);
        BoardAccessPolicy policy = new BoardAccessPolicy(admins);
        InquiryLegacyWritePolicy inquiry = new InquiryLegacyWritePolicy(config);
        UserBlockService blocks = mock(UserBlockService.class);
        PostSummaryAssembler summaries = mock(PostSummaryAssembler.class);
        SearchUserLookupPolicy userLookup = new SearchUserLookupPolicy(users);
        search = new SearchService(mock(SearchStatisticRepository.class), mock(SearchStatisticCommandService.class),
                mock(RecentSearchCommandService.class), mock(SearchPersonalizationRepository.class), posts,
                boards, blocks, summaries, policy, inquiry, mock(SearchRecordEventPublisher.class), userLookup);
        preview = new SearchPreviewReadService(users, posts, mock(CommentRepository.class), boards, policy,
                inquiry, blocks, summaries, new IntegratedSearchAssembler(), userLookup,
                mock(CommentSummaryAssembler.class));
        viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);
        board = Board.builder().boardName("Board").boardUrl("test-board").isPublic(true).build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        when(boards.findByBoardUrl(anyString())).thenReturn(Optional.of(board));
        when(users.findById(1L)).thenReturn(Optional.of(viewer));
        when(posts.searchPosts(anyString(), anyString(), anyString(), any(), any(), any(), any(),
                anyBoolean(), any(), any())).thenReturn(Page.empty());
        when(summaries.assembleSearchPage(any())).thenReturn(Page.empty());
        when(config.getConfigFresh(GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY)).thenReturn("N");
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void privateBoardManagerIsQueriedOnceAndCanSeeSecrets(Endpoint endpoint) {
        ReflectionTestUtils.setField(board, "isPublic", false);
        when(admins.existsByUser_UserIdAndBoard_BoardIdAndIsActive(1L, 10L, true)).thenReturn(true);
        run(endpoint, 1L);
        verifySingleAdminQuery();
        verifySearchIncludesSecrets(true);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void inactiveBoardManagerIsQueriedOnceAndCanSeeSecrets(Endpoint endpoint) {
        ReflectionTestUtils.setField(board, "isActive", false);
        when(admins.existsByUser_UserIdAndBoard_BoardIdAndIsActive(1L, 10L, true)).thenReturn(true);
        run(endpoint, 1L);
        verifySingleAdminQuery();
        verifySearchIncludesSecrets(true);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void publicBoardNormalViewerIsQueriedOnceAndCannotSeeSecrets(Endpoint endpoint) {
        run(endpoint, 1L);
        verifySingleAdminQuery();
        verifySearchIncludesSecrets(false);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void restrictedBoardDenialStillStopsBeforeInquiryPolicy(Endpoint endpoint) {
        board.updateBoardUrl("inquiry");
        ReflectionTestUtils.setField(board, "isPublic", false);
        assertError(endpoint, 1L, ErrorCode.BOARD_NOT_FOUND);
        verifySingleAdminQuery();
        verifyNoInteractions(config, posts);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void anonymousPublicBoardNeedsNoAdminLookup(Endpoint endpoint) {
        run(endpoint, null);
        verifyNoInteractions(users, admins);
        verifySearchIncludesSecrets(false);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void anonymousPrivateBoardRemainsHidden(Endpoint endpoint) {
        ReflectionTestUtils.setField(board, "isPublic", false);
        assertError(endpoint, null, ErrorCode.BOARD_NOT_FOUND);
        verifyNoInteractions(users, admins, config, posts);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void usableSuperAdminCanReadInactivePrivateInquiryWithoutAdminLookup(Endpoint endpoint) {
        viewer.grantSuperAdminRole();
        board.updateBoardUrl("inquiry");
        ReflectionTestUtils.setField(board, "isActive", false);
        ReflectionTestUtils.setField(board, "isPublic", false);
        run(endpoint, 1L);
        verifyNoInteractions(admins);
        verify(config).getConfigFresh(GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY);
        verifySearchIncludesSecrets(true);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void suspendedSuperAdminDoesNotBypassRestrictedBoardAccess(Endpoint endpoint) {
        viewer.grantSuperAdminRole();
        viewer.suspend();
        ReflectionTestUtils.setField(board, "isPublic", false);
        assertError(endpoint, 1L, ErrorCode.BOARD_NOT_FOUND);
        verifySingleAdminQuery();
        verifyNoInteractions(config, posts);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void disabledPublicInquiryFailsBeforeSecretAdminLookup(Endpoint endpoint) {
        board.updateBoardUrl("inquiry");
        assertError(endpoint, 1L, ErrorCode.BOARD_NOT_FOUND);
        verify(config).getConfigFresh(GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY);
        verifyNoInteractions(admins, posts);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void disabledPrivateInquiryChecksReadAccessBeforeInquiryPolicy(Endpoint endpoint) {
        board.updateBoardUrl("inquiry");
        ReflectionTestUtils.setField(board, "isPublic", false);
        when(admins.existsByUser_UserIdAndBoard_BoardIdAndIsActive(1L, 10L, true)).thenReturn(true);
        assertError(endpoint, 1L, ErrorCode.BOARD_NOT_FOUND);
        InOrder order = inOrder(admins, config);
        order.verify(admins).existsByUser_UserIdAndBoard_BoardIdAndIsActive(1L, 10L, true);
        order.verify(config).getConfigFresh(GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY);
        verifyNoMoreInteractions(admins, config);
        verifyNoInteractions(posts);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void enabledPublicInquiryChecksPolicyBeforeSecretAdminLookup(Endpoint endpoint) {
        board.updateBoardUrl("inquiry");
        when(config.getConfigFresh(GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY)).thenReturn("Y");
        run(endpoint, 1L);
        InOrder order = inOrder(config, admins);
        order.verify(config).getConfigFresh(GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY);
        order.verify(admins).existsByUser_UserIdAndBoard_BoardIdAndIsActive(1L, 10L, true);
        verifySearchIncludesSecrets(false);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void missingBoardIsCheckedBeforeViewer(Endpoint endpoint) {
        when(boards.findByBoardUrl(anyString())).thenReturn(Optional.empty());
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertError(endpoint, 1L, ErrorCode.BOARD_NOT_FOUND);
        verifyNoInteractions(users, admins, config, posts);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void missingViewerIsCheckedBeforeBoardAccess(Endpoint endpoint) {
        ReflectionTestUtils.setField(board, "isPublic", false);
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertError(endpoint, 1L, ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(admins, config, posts);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void adminResultIsNotReusedByAnotherSearch(Endpoint endpoint) {
        ReflectionTestUtils.setField(board, "isPublic", false);
        when(admins.existsByUser_UserIdAndBoard_BoardIdAndIsActive(1L, 10L, true)).thenReturn(true, false);
        run(endpoint, 1L);
        assertError(endpoint, 1L, ErrorCode.BOARD_NOT_FOUND);
        verify(admins, times(2)).existsByUser_UserIdAndBoard_BoardIdAndIsActive(1L, 10L, true);
        verifySearchIncludesSecrets(true);
    }

    private void run(Endpoint endpoint, Long viewerId) {
        if (endpoint == Endpoint.POSTS) {
            search.searchPosts("needle", "TITLE", board.getBoardUrl(), null, null, null, null,
                    0, 5, Sort.unsorted(), viewerId);
        } else {
            preview.integratedSearch("needle", "TITLE", board.getBoardUrl(), null, null, null, null,
                    0, 5, Sort.unsorted(), viewerId);
        }
    }

    private void assertError(Endpoint endpoint, Long viewerId, ErrorCode code) {
        assertThatThrownBy(() -> run(endpoint, viewerId)).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", code);
    }

    private void verifySingleAdminQuery() {
        verify(admins).existsByUser_UserIdAndBoard_BoardIdAndIsActive(1L, 10L, true);
        verifyNoMoreInteractions(admins);
    }

    private void verifySearchIncludesSecrets(boolean includeSecrets) {
        verify(posts).searchPosts(eq("needle"), eq("TITLE"), eq(board.getBoardUrl()), any(), any(), any(), any(),
                eq(includeSecrets), any(), any());
    }
}
