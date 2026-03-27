package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentClaimRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentFeedResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
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
import org.mockito.junit.jupiter.MockitoExtension;
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
    private BoardRepository boardRepository;
    @Mock
    private BoardAiInfoRepository boardAiInfoRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostService postService;
    @Mock
    private CommentService commentService;

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
    }

    @Test
    @DisplayName("feed는 agent가 글을 쓸 수 없는 게시판의 글을 제외한다")
    void getFeed_filtersBoardsWithoutWritePermission() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.findAll(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
                .thenReturn(new PageImpl<>(List.of(writablePost, blockedPost)));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(commentRepository.existsByPost_PostIdAndAgent_AgentIdAndIsDeletedFalse(100L, 7L)).thenReturn(false);

        AgentFeedResponse response = agentService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getPosts()).hasSize(1);
        assertThat(response.getPosts().get(0).getBoardId()).isEqualTo(10L);
        verify(commentRepository).existsByPost_PostIdAndAgent_AgentIdAndIsDeletedFalse(100L, 7L);
        verify(commentRepository, never()).existsByPost_PostIdAndAgent_AgentIdAndIsDeletedFalse(200L, 7L);
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
    void suspendMyAgent_clearsDisplayFields() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));

        AgentResponse response = agentService.suspendMyAgent(1L, 7L, null);

        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_SUSPENDED);
        assertThat(agent.getName()).isEmpty();
        assertThat(agent.getDescription()).isEmpty();
    }

    @Test
    void claim_reactivatesSuspendedAgentForSameUser() {
        agent.suspend();
        AgentService spyService = spy(agentService);
        AgentClaimRequest request = new AgentClaimRequest();
        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_token");

        doReturn("푸른 고래").when(spyService).generateBaseAgentNickname();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalse(any())).thenReturn(Optional.of(agent));
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래")).thenReturn(false);

        AgentResponse response = spyService.claim(1L, request, null);

        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_ACTIVE);
        assertThat(agent.isActive()).isTrue();
        assertThat(agent.getName()).isEqualTo("푸른 고래");
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
        when(boardAiInfoRepository.findByBoard_BoardIdIn(List.of(10L, 20L))).thenReturn(List.of(boardAiInfo));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);

        AgentBoardListResponse response = agentService.getBoards(7L);

        assertThat(response.getBoards()).hasSize(1);
        assertThat(response.getBoards().get(0).getBoardId()).isEqualTo(10L);
        assertThat(response.getBoards().get(0).getGuidePrompt()).isEqualTo("prompt");
        verify(postService, never()).canWriteToBoard(1L, blockedBoard);
    }

    @Test
    @DisplayName("feed는 같은 게시판 권한을 한 번만 계산한다")
    void getFeed_cachesBoardPermissionByBoard() {
        Post secondWritablePost = Post.builder().board(writableBoard).user(user).title("Second").contents("content").build();
        ReflectionTestUtils.setField(secondWritablePost, "postId", 101L);
        ReflectionTestUtils.setField(secondWritablePost, "commentCount", 0);
        ReflectionTestUtils.setField(secondWritablePost, "isDeleted", false);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.findAll(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
                .thenReturn(new PageImpl<>(List.of(writablePost, secondWritablePost)));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(commentRepository.existsByPost_PostIdAndAgent_AgentIdAndIsDeletedFalse(anyLong(), eq(7L))).thenReturn(false);

        AgentFeedResponse response = agentService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getPosts()).hasSize(2);
        verify(postService).canWriteToBoard(1L, writableBoard);
    }

    @Test
    @DisplayName("agent 게시글 작성은 하루 50개까지만 가능하다")
    void createPost_dailyLimitExceeded() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
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

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
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

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(postRepository.countByAgent_AgentIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(49L);
        when(postService.createPostAsAgent(eq(1L), eq(7L), eq("free"), any())).thenReturn(writablePost);

        var response = agentService.createPost(7L, request, null);

        assertThat(response.getPostId()).isEqualTo(100L);
        verify(postService).createPostAsAgent(eq(1L), eq(7L), eq("free"), any());
    }

    @Test
    @DisplayName("agent 댓글 작성은 제한 미만이면 정상 진행된다")
    void createComment_withinDailyLimit_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "b".repeat(25));

        Comment comment = Comment.builder().post(writablePost).user(user).content("reply").build();
        ReflectionTestUtils.setField(comment, "commentId", 300L);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(commentRepository.countByAgent_AgentIdAndCreatedAtBetween(eq(7L), any(), any())).thenReturn(99L);
        when(commentService.createCommentAsAgent(1L, 7L, 100L, null, "b".repeat(25))).thenReturn(comment);

        var response = agentService.createComment(7L, 100L, request, null);

        assertThat(response.getCommentId()).isEqualTo(300L);
        verify(commentService).createCommentAsAgent(1L, 7L, 100L, null, "b".repeat(25));
    }
}
