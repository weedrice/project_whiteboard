package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentClaimRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private AgentActivityLogRepository agentActivityLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private BoardAiInfoRepository boardAiInfoRepository;
    @Mock
    private BoardCategoryRepository boardCategoryRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostService postService;
    @Mock
    private CommentService commentService;
    @Spy
    @InjectMocks
    private AgentPostSummaryEnricher agentPostSummaryEnricher;

    @InjectMocks
    private AgentService agentService;

    private User user;
    private Agent agent;
    private Board writableBoard;
    private Board blockedBoard;
    private Post writablePost;
    private Post blockedPost;

    @BeforeEach
    void setUp() {
        agentPostSummaryEnricher = new AgentPostSummaryEnricher(commentRepository);
        ReflectionTestUtils.setField(agentService, "agentPostSummaryEnricher", agentPostSummaryEnricher);

        lenient().when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(any(), anyLong()))
                .thenReturn(List.of());

        user = User.builder().loginId("user").displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isEmailVerified", true);

        agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 7L);

        writableBoard = Board.builder().boardName("Writable").boardUrl("free").creator(user).build();
        ReflectionTestUtils.setField(writableBoard, "boardId", 10L);
        ReflectionTestUtils.setField(writableBoard, "isActive", true);
        ReflectionTestUtils.setField(writableBoard, "isPublic", true);
        ReflectionTestUtils.setField(writableBoard, "agentUseYn", true);

        blockedBoard = Board.builder().boardName("Blocked").boardUrl("notice").creator(user).build();
        ReflectionTestUtils.setField(blockedBoard, "boardId", 20L);
        ReflectionTestUtils.setField(blockedBoard, "isActive", true);
        ReflectionTestUtils.setField(blockedBoard, "isPublic", true);
        ReflectionTestUtils.setField(blockedBoard, "agentUseYn", false);

        writablePost = Post.builder().board(writableBoard).user(user).title("Writable post").contents("content").build();
        ReflectionTestUtils.setField(writablePost, "postId", 100L);
        ReflectionTestUtils.setField(writablePost, "commentCount", 1);
        ReflectionTestUtils.setField(writablePost, "isDeleted", false);

        blockedPost = Post.builder().board(blockedBoard).user(user).title("Blocked post").contents("content").build();
        ReflectionTestUtils.setField(blockedPost, "postId", 200L);
        ReflectionTestUtils.setField(blockedPost, "commentCount", 2);
        ReflectionTestUtils.setField(blockedPost, "isDeleted", false);

        lenient().when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(any(), any(), eq(true)))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("feed는 agent가 글을 쓸 수 없는 게시판의 글을 제외한다")
    void getFeed_filtersBoardsWithoutWritePermission() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAsc(true, true))
                .thenReturn(List.of(writableBoard, blockedBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(postRepository.findByBoard_BoardIdInAndIsDeletedFalseOrderByCreatedAtDesc(
                List.of(10L),
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
                .thenReturn(new PageImpl<>(List.of(writablePost), PageRequest.of(0, 10), 1));
        when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(List.of(100L), 7L))
                .thenReturn(List.of());

        Page<PostSummary> response = agentService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getBoardId()).isEqualTo(10L);
        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(commentRepository).findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(List.of(100L), 7L);
    }

    @Test
    void claim_requiresVerifiedEmail() {
        ReflectionTestUtils.setField(user, "isEmailVerified", false);

        AgentClaimRequest request = new AgentClaimRequest();
        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentService.claim(1L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);

        verify(agentRepository, never()).findByAgentTokenHashAndIsDeletedFalse(any());
    }

    @Test
    void register_generatesRandomKoreanNicknameWhenNameIsBlank() {
        AgentService spyService = spy(agentService);
        AgentRegisterRequest request = new AgentRegisterRequest();
        ReflectionTestUtils.setField(request, "description", "desc");

        doReturn("푸른 고래").when(spyService).generateBaseAgentNickname();
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래")).thenReturn(false);

        spyService.register(request, null);

        verify(agentRepository).save(argThat(savedAgent -> "푸른 고래".equals(savedAgent.getName())));
    }

    @Test
    void register_appendsSuffixWhenGeneratedNicknameAlreadyExists() {
        AgentService spyService = spy(agentService);
        AgentRegisterRequest request = new AgentRegisterRequest();
        ReflectionTestUtils.setField(request, "description", "desc");

        doReturn("푸른 고래").when(spyService).generateBaseAgentNickname();
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래")).thenReturn(true);
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래 2")).thenReturn(false);

        spyService.register(request, null);

        verify(agentRepository).existsByNameAndIsDeletedFalse("푸른 고래");
        verify(agentRepository).existsByNameAndIsDeletedFalse("푸른 고래 2");
        verify(agentRepository).save(argThat(savedAgent -> "푸른 고래 2".equals(savedAgent.getName())));
    }

    @Test
    void register_ignoresRequestedNameAndAlwaysGeneratesNickname() {
        AgentService spyService = spy(agentService);
        AgentRegisterRequest request = new AgentRegisterRequest();
        ReflectionTestUtils.setField(request, "description", "desc");

        doReturn("푸른 고래").when(spyService).generateBaseAgentNickname();
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래")).thenReturn(false);

        spyService.register(request, null);

        verify(agentRepository).save(argThat(savedAgent -> "푸른 고래".equals(savedAgent.getName())));
    }

    @Test
    void suspendMyAgent_keepsDisplayFields() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));

        AgentResponse response = agentService.suspendMyAgent(1L, 7L, null);

        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_SUSPENDED);
        assertThat(agent.getName()).isEqualTo("agent");
        assertThat(agent.getDescription()).isEqualTo("desc");
    }

    @Test
    void claim_reactivatesSuspendedAgentForSameUser() {
        agent.suspend();
        AgentService spyService = spy(agentService);
        AgentClaimRequest request = new AgentClaimRequest();
        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalse(any())).thenReturn(Optional.of(agent));

        AgentResponse response = spyService.claim(1L, request, null);

        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_ACTIVE);
        assertThat(agent.isActive()).isTrue();
        assertThat(agent.getName()).isEqualTo("agent");
    }

    @Test
    void claim_softDeletesExistingAgentsForUserWhenClaimingNewCode() {
        Agent previousAgent = Agent.builder()
                .user(user)
                .agentTokenHash("old-hash")
                .name("old-agent")
                .description("old-desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(previousAgent, "agentId", 3L);

        Agent pendingAgent = Agent.builder()
                .agentTokenHash("new-hash")
                .name("new-agent")
                .description("new-desc")
                .status(Agent.STATUS_PENDING_CLAIM)
                .build();
        ReflectionTestUtils.setField(pendingAgent, "agentId", 9L);

        AgentClaimRequest request = new AgentClaimRequest();
        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_new");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalse(any())).thenReturn(Optional.of(pendingAgent));
        when(agentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user))
                .thenReturn(List.of(previousAgent));

        AgentResponse response = agentService.claim(1L, request, null);

        assertThat(response.getAgentId()).isEqualTo(9L);
        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_ACTIVE);
        assertThat(previousAgent.getIsDeleted()).isTrue();
        assertThat(pendingAgent.getUser()).isEqualTo(user);
        verify(agentActivityLogRepository, times(2)).save(any());
    }

    @Test
    void getMyAgents_usesOnlyUndeletedAgents() {
        Agent activeAgent = Agent.builder()
                .user(user)
                .agentTokenHash("hash-1")
                .name("active-agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(activeAgent, "agentId", 11L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user))
                .thenReturn(List.of(activeAgent));

        var response = agentService.getMyAgents(1L);

        assertThat(response.getAgents()).hasSize(1);
        assertThat(response.getAgents().get(0).getAgentId()).isEqualTo(11L);
        verify(agentRepository).findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    }

    @Test
    void getBoards_returnsOnlyWritableAgentEnabledBoards() {
        BoardAiInfo boardAiInfo = BoardAiInfo.builder()
                .board(writableBoard)
                .guidePrompt("prompt")
                .build();
        ReflectionTestUtils.setField(boardAiInfo, "boardId", 10L);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAsc(true, true))
                .thenReturn(List.of(writableBoard, blockedBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(10L, 20L), true)).thenReturn(List.of(
                        defaultCategory(writableBoard, Role.BOARD_ADMIN),
                        defaultCategory(blockedBoard, Role.USER)));
        when(boardAiInfoRepository.findByBoard_BoardIdIn(List.of(10L, 20L))).thenReturn(List.of(boardAiInfo));
        when(postRepository.countActiveByBoardIds(List.of(10L, 20L)))
                .thenReturn(List.of(boardPostCount(10L, 7L), boardPostCount(20L, 3L)));

        AgentBoardListResponse response = agentService.getBoards(7L);

        assertThat(response.getBoards()).hasSize(1);
        assertThat(response.getBoards().get(0).getBoardId()).isEqualTo(10L);
        assertThat(response.getBoards().get(0).getGuidePrompt()).isEqualTo("prompt");
        assertThat(response.getBoards().get(0).getPostCount()).isEqualTo(7L);
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getBoards_includesBoardAdminOnlyBoardForActiveAdmin() {
        User otherUser = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);

        Board managedBoard = Board.builder().boardName("Managed").boardUrl("managed").creator(otherUser).build();
        ReflectionTestUtils.setField(managedBoard, "boardId", 30L);
        ReflectionTestUtils.setField(managedBoard, "isActive", true);
        ReflectionTestUtils.setField(managedBoard, "isPublic", true);
        ReflectionTestUtils.setField(managedBoard, "agentUseYn", true);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAsc(true, true))
                .thenReturn(List.of(managedBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(30L), true)).thenReturn(List.of(defaultCategory(managedBoard, Role.BOARD_ADMIN)));
        when(boardAiInfoRepository.findByBoard_BoardIdIn(List.of(30L))).thenReturn(List.of());
        when(postRepository.countActiveByBoardIds(List.of(30L))).thenReturn(List.of(boardPostCount(30L, 2L)));
        when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(user, List.of(30L), true))
                .thenReturn(List.of(activeAdmin(user, managedBoard)));

        AgentBoardListResponse response = agentService.getBoards(7L);

        assertThat(response.getBoards()).hasSize(1);
        assertThat(response.getBoards().get(0).getBoardId()).isEqualTo(30L);
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getBoards_excludesSuperAdminOnlyBoardForNormalAgent() {
        User otherUser = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);

        Board superAdminOnlyBoard = Board.builder().boardName("Super").boardUrl("super").creator(otherUser).build();
        ReflectionTestUtils.setField(superAdminOnlyBoard, "boardId", 40L);
        ReflectionTestUtils.setField(superAdminOnlyBoard, "isActive", true);
        ReflectionTestUtils.setField(superAdminOnlyBoard, "isPublic", true);
        ReflectionTestUtils.setField(superAdminOnlyBoard, "agentUseYn", true);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAsc(true, true))
                .thenReturn(List.of(superAdminOnlyBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(40L), true)).thenReturn(List.of(defaultCategory(superAdminOnlyBoard, Role.SUPER_ADMIN)));
        when(boardAiInfoRepository.findByBoard_BoardIdIn(List.of(40L))).thenReturn(List.of());
        when(postRepository.countActiveByBoardIds(List.of(40L))).thenReturn(List.of(boardPostCount(40L, 1L)));

        AgentBoardListResponse response = agentService.getBoards(7L);

        assertThat(response.getBoards()).isEmpty();
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    @DisplayName("feed는 같은 게시판 권한을 한 번만 계산한다")
    void getFeed_cachesBoardPermissionByBoard() {
        Post secondWritablePost = Post.builder().board(writableBoard).user(user).title("Second").contents("content").build();
        ReflectionTestUtils.setField(secondWritablePost, "postId", 101L);
        ReflectionTestUtils.setField(secondWritablePost, "commentCount", 0);
        ReflectionTestUtils.setField(secondWritablePost, "isDeleted", false);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAsc(true, true))
                .thenReturn(List.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(postRepository.findByBoard_BoardIdInAndIsDeletedFalseOrderByCreatedAtDesc(
                List.of(10L),
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
                .thenReturn(new PageImpl<>(List.of(writablePost, secondWritablePost), PageRequest.of(0, 10), 2));
        when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(
                List.of(100L, 101L), 7L)).thenReturn(List.of());

        Page<PostSummary> response = agentService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(2);
        verify(postService).canWriteToBoard(1L, writableBoard);
    }

    @Test
    void getFeed_returnsEmptyPageWhenRequestedBoardIsNotAccessible() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardId(20L)).thenReturn(Optional.of(blockedBoard));

        Page<PostSummary> response = agentService.getFeed(7L, 20L, PageRequest.of(0, 10));

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        verify(postRepository, never()).findByBoard_BoardIdInAndIsDeletedFalseOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("agent 게시글 작성은 하루 50개까지만 가능하다")
    void createPost_dailyLimitExceeded() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(postRepository.countByAgent_AgentIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(50L);

        assertThatThrownBy(() -> agentService.createPost(7L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);

        verify(postService, never()).createPostAsAgent(anyLong(), anyLong(), eq("free"), any());
    }

    @Test
    @DisplayName("agent 댓글 작성은 하루 100개까지만 가능하다")
    void createComment_dailyLimitExceeded() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "b".repeat(25));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(postService.getPostById(100L, 1L, false)).thenReturn(writablePost);
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(commentRepository.countByAgent_AgentIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(100L);

        assertThatThrownBy(() -> agentService.createComment(7L, 100L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);

        verify(commentService, never()).createCommentAsAgent(anyLong(), anyLong(), eq(100L), eq(null), any());
    }

    @Test
    @DisplayName("agent 게시글 작성은 제한 미만이면 정상 진행된다")
    void createPost_withinDailyLimit_success() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(postRepository.countByAgent_AgentIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(49L);
        when(postService.createPostAsAgent(eq(1L), eq(7L), eq("free"), any())).thenReturn(writablePost);

        var response = agentService.createPost(7L, request, null);

        assertThat(response.getPostId()).isEqualTo(100L);
        verify(agentRepository).findByAgentIdForUpdate(7L);
        verify(postService).createPostAsAgent(eq(1L), eq(7L), eq("free"), any());
    }

    @Test
    void createPost_convertsPlainTextLineBreaksToHtmlParagraphs() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "첫 줄\n둘째 줄\n\n다음 문단");

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(postRepository.countByAgent_AgentIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(0L);
        when(postService.createPostAsAgent(eq(1L), eq(7L), eq("free"), any())).thenReturn(writablePost);

        agentService.createPost(7L, request, null);

        verify(postService).createPostAsAgent(eq(1L), eq(7L), eq("free"), argThat(postRequest ->
                "<p>첫 줄<br>둘째 줄</p><p>다음 문단</p>".equals(postRequest.getContents())));
    }

    @Test
    @DisplayName("agent 댓글 작성은 제한 미만이면 정상 진행된다")
    void createComment_withinDailyLimit_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "b".repeat(25));

        Comment comment = Comment.builder().post(writablePost).user(user).content("reply").build();
        ReflectionTestUtils.setField(comment, "commentId", 300L);

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(postService.getPostById(100L, 1L, false)).thenReturn(writablePost);
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(commentRepository.countByAgent_AgentIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(99L);
        when(commentService.createCommentAsAgent(1L, 7L, 100L, null, "b".repeat(25))).thenReturn(comment);

        var response = agentService.createComment(7L, 100L, request, null);

        assertThat(response.getCommentId()).isEqualTo(300L);
        verify(agentRepository).findByAgentIdForUpdate(7L);
        verify(commentService).createCommentAsAgent(1L, 7L, 100L, null, "b".repeat(25));
    }

    @Test
    void createReply_withinDailyLimit_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "reply");

        Comment parentComment = Comment.builder().post(writablePost).user(user).content("parent").build();
        ReflectionTestUtils.setField(parentComment, "commentId", 500L);
        ReflectionTestUtils.setField(parentComment, "isDeleted", false);

        Comment reply = Comment.builder().post(writablePost).user(user).content("reply").build();
        ReflectionTestUtils.setField(reply, "commentId", 501L);

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(commentRepository.findByIdWithRelations(500L)).thenReturn(Optional.of(parentComment));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(commentRepository.countByAgent_AgentIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(99L);
        when(commentService.createCommentAsAgent(1L, 7L, 100L, 500L, "reply")).thenReturn(reply);

        var response = agentService.createReply(7L, 500L, request, null);

        assertThat(response.getCommentId()).isEqualTo(501L);
        verify(agentRepository).findByAgentIdForUpdate(7L);
        verify(commentService).createCommentAsAgent(1L, 7L, 100L, 500L, "reply");
    }

    private BoardCategory defaultCategory(Board board, String minWriteRole) {
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("일반")
                .sortOrder(1)
                .minWriteRole(minWriteRole)
                .build();
        ReflectionTestUtils.setField(category, "categoryId", board.getBoardId() + 1000);
        return category;
    }

    private Admin activeAdmin(User adminUser, Board board) {
        Admin admin = Admin.builder()
                .user(adminUser)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "adminId", board.getBoardId() + 2000);
        return admin;
    }

    private PostRepository.BoardPostCountProjection boardPostCount(Long boardId, Long postCount) {
        return new PostRepository.BoardPostCountProjection() {
            @Override
            public Long getBoardId() {
                return boardId;
            }

            @Override
            public Long getPostCount() {
                return postCount;
            }
        };
    }
}
