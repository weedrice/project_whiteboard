package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentBoardListReadService;
import com.weedrice.whiteboard.domain.agent.service.AgentHomeReadModel;
import com.weedrice.whiteboard.domain.agent.service.AgentNoteService;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.service.BoardCategoryWritePolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AgentHomeReadModelServiceTest {
    private static final List<Long> CANDIDATE_IDS = List.of(10L, 20L, 30L);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 20, 12, 34);
    private BoardRepository boards;
    private BoardCategoryRepository categories;
    private BoardAiInfoRepository boardAiInfo;
    private AdminRepository admins;
    private UserRepository users;
    private PostRepository posts;
    private CommentRepository comments;
    private UserBlockService blocks;
    private AgentNoteService notes;
    private AgentBoardAccessService access;
    private AgentBoardListReadService boardList;
    private AgentHomeReadModelService home;
    private User owner;
    private Agent agent;
    private Board publicBoard;
    private Board adminBoard;
    private Board superAdminBoard;

    @BeforeEach
    void setUp() {
        boards = mock(BoardRepository.class);
        categories = mock(BoardCategoryRepository.class);
        boardAiInfo = mock(BoardAiInfoRepository.class);
        admins = mock(AdminRepository.class);
        users = mock(UserRepository.class);
        posts = mock(PostRepository.class);
        comments = mock(CommentRepository.class);
        blocks = mock(UserBlockService.class);
        notes = mock(AgentNoteService.class);
        BoardAccessPolicy boardPolicy = new BoardAccessPolicy(admins);
        access = new AgentBoardAccessService(admins, boards, categories,
                new PostAuthorCommandPolicy(boardPolicy, categories, new BoardCategoryWritePolicy(boardPolicy)), users);
        boardList = new AgentBoardListReadService(
                boards, boardAiInfo, new AgentBoardPostCountIntegrationAdapter(posts), access);
        home = new AgentHomeReadModelService(posts, comments, blocks, boardList, notes);
        owner = User.builder().displayName("Owner").build();
        ReflectionTestUtils.setField(owner, "userId", 1L);
        agent = Agent.builder().user(owner).name("Agent").status(Agent.STATUS_ACTIVE).build();
        ReflectionTestUtils.setField(agent, "agentId", 7L);
        publicBoard = board(10L);
        adminBoard = board(20L);
        superAdminBoard = board(30L);
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(boards.findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc())
                .thenReturn(List.of(publicBoard, adminBoard, superAdminBoard));
        when(categories.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(CANDIDATE_IDS, true))
                .thenReturn(List.of(category(publicBoard, Role.USER), category(adminBoard, Role.BOARD_ADMIN),
                        category(superAdminBoard, Role.SUPER_ADMIN)));
        when(admins.findByUserAndBoard_BoardIdInAndIsActive(owner, CANDIDATE_IDS, true))
                .thenReturn(List.of(Admin.builder().user(owner).board(adminBoard).build(),
                        Admin.builder().user(owner).board(superAdminBoard).build()));
        when(posts.findByAgentIdAndIsDeleted(eq(7L), eq(false), any(Pageable.class))).thenReturn(Page.empty());
        when(posts.findAgentFeedByBoardIds(anyCollection(), any(), any(), eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(blocks.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(99L));
    }

    @Test
    void homeReusesAccessBatchesAndOnlyLoadsCommentFlagsForFeed() {
        when(posts.findByAgentIdAndIsDeleted(eq(7L), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(100L, publicBoard))));
        when(posts.findAgentFeedByBoardIds(eq(List.of(10L, 20L)), eq(List.of(99L)), eq(Set.of(20L)), eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(post(200L, publicBoard), post(201L, adminBoard))));
        when(comments.findDistinctPostIdsByPostIdInAndAgentIdAndIsDeletedFalse(List.of(200L, 201L), 7L))
                .thenReturn(List.of(201L));

        AgentHomeReadModel result = home.collect(agent);

        assertThat(result.hasWritableBoardPermission()).isTrue();
        assertThat(result.myRecentPosts()).singleElement().usingRecursiveComparison().isEqualTo(
                AgentHomeResponse.MyRecentPost.builder().postId(100L).title("Post 100")
                        .boardId(10L).boardName("Board 10").commentCount(2).likeCount(3)
                        .createdAt(CREATED_AT).build());
        assertThat(result.recentFeed()).usingRecursiveComparison().isEqualTo(List.of(
                expectedFeed(200L, 10L, false), expectedFeed(201L, 20L, true)));
        assertThat(result.recommendedBoards()).extracting(AgentHomeResponse.RecommendedBoard::getBoardId)
                .containsExactly(10L, 20L);
        verify(boards).findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc();
        verify(categories).findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(CANDIDATE_IDS, true);
        verify(admins).findByUserAndBoard_BoardIdInAndIsActive(owner, CANDIDATE_IDS, true);
        verifyNoMoreInteractions(boards, categories, admins);
        verify(users).findById(1L);
        verifyNoMoreInteractions(users); // No author batch is needed for either home DTO.
        verify(comments).findDistinctPostIdsByPostIdInAndAgentIdAndIsDeletedFalse(List.of(200L, 201L), 7L);
        verify(comments).findUnreadAgentPostActivities(eq(7L), any());
        verifyNoMoreInteractions(comments);
        verify(posts).findAgentFeedByBoardIds(eq(List.of(10L, 20L)), eq(List.of(99L)), eq(Set.of(20L)), eq(1L), any());
    }

    @Test
    void superAdminSeesAllWritableBoardsWithoutAdminLookup() {
        ReflectionTestUtils.setField(owner, "isSuperAdmin", true);
        home.collect(agent);
        verify(posts).findAgentFeedByBoardIds(eq(CANDIDATE_IDS), eq(List.of(99L)),
                eq(Set.of(10L, 20L, 30L)), eq(1L), any());
        verifyNoInteractions(admins);
    }

    @Test
    void normalOwnerCannotWriteAdminDefaultCategory() {
        when(admins.findByUserAndBoard_BoardIdInAndIsActive(owner, CANDIDATE_IDS, true)).thenReturn(List.of());
        home.collect(agent);
        verify(posts).findAgentFeedByBoardIds(eq(List.of(10L)), eq(List.of(99L)), eq(Set.of()), eq(1L), any());
    }

    @Test
    void emptyCandidatesSkipOwnerAndAccessLookups() {
        when(boards.findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc())
                .thenReturn(List.of());
        AgentHomeReadModel result = home.collect(agent);
        assertThat(result.hasWritableBoardPermission()).isFalse();
        assertThat(result.recentFeed()).isEmpty();
        verify(boards).findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc();
        verifyNoInteractions(users, categories, admins, blocks, boardAiInfo);
        verify(posts, never()).findAgentFeedByBoardIds(anyCollection(), any(), any(), anyLong(), any());
    }

    @Test
    void noWritableBoardSkipsFeedAndItsCommentFlags() {
        when(categories.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(CANDIDATE_IDS, true))
                .thenReturn(List.of(category(publicBoard, Role.SUPER_ADMIN), category(adminBoard, Role.SUPER_ADMIN),
                        category(superAdminBoard, Role.SUPER_ADMIN)));
        AgentHomeReadModel result = home.collect(agent);
        assertThat(result.hasWritableBoardPermission()).isFalse();
        assertThat(result.recentFeed()).isEmpty();
        verifyNoInteractions(blocks, boardAiInfo);
        verify(posts, never()).findAgentFeedByBoardIds(anyCollection(), any(), any(), anyLong(), any());
        verify(comments, never()).findDistinctPostIdsByPostIdInAndAgentIdAndIsDeletedFalse(anyList(), anyLong());
    }

    @Test
    void missingOwnerStillFailsBeforeOtherHomeSections() {
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> home.collect(agent)).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(notes, posts, comments, admins, blocks, boardAiInfo);
    }

    @Test
    void invalidAccessInputsKeepTheirEmptyShortCircuit() {
        assertThat(boardList.getWritableBoardsWithAccess(null).response().getBoards()).isEmpty();
        Agent unclaimed = Agent.builder().name("Unclaimed").build();
        assertThat(boardList.getWritableBoardsWithAccess(unclaimed).response().getBoards()).isEmpty();
        assertThat(access.resolveBoardAccess(agent, null, Map.of()).writableBoardIds()).isEmpty();
        assertThat(access.resolveBoardAccess(agent, List.of(), Map.of()).adminBoardIds()).isEmpty();
        verifyNoInteractions(users, admins, posts, blocks, boardAiInfo);
    }

    @Test
    void standaloneBoardListAndFeedAccessKeepDefaultCategoryRules() {
        assertThat(boardList.getWritableBoards(agent).getBoards()).extracting(AgentBoardItem::getBoardId)
                .containsExactly(10L, 20L);
        assertThat(access.getAccessibleFeedBoards(agent, null)).containsExactly(publicBoard, adminBoard);
    }

    private Board board(Long id) {
        Board board = Board.builder().boardName("Board " + id).boardUrl("board-" + id)
                .description("Guide " + id).agentUseYn(true).isPublic(true).build();
        ReflectionTestUtils.setField(board, "boardId", id);
        return board;
    }

    private BoardCategory category(Board board, String role) {
        BoardCategory category = BoardCategory.builder().board(board).name("Default")
                .minWriteRole(role).isDefault(true).sortOrder(0).build();
        ReflectionTestUtils.setField(category, "categoryId", board.getBoardId());
        return category;
    }

    private Post post(Long id, Board board) {
        Post post = Post.builder().user(owner).agent(agent).board(board).title("Post " + id)
                .contents("<p>Preview " + id + "</p>").build();
        ReflectionTestUtils.setField(post, "postId", id);
        ReflectionTestUtils.setField(post, "commentCount", 2);
        ReflectionTestUtils.setField(post, "likeCount", 3);
        ReflectionTestUtils.setField(post, "createdAt", CREATED_AT);
        return post;
    }

    private AgentHomeResponse.RecentFeedItem expectedFeed(Long postId, Long boardId, boolean commented) {
        return AgentHomeResponse.RecentFeedItem.builder().postId(postId).title("Post " + postId)
                .contentPreview("Preview " + postId).boardId(boardId).boardName("Board " + boardId)
                .commentCount(2).likeCount(3).createdAt(CREATED_AT).hasMyComment(commented).build();
    }
}
