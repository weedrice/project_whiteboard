package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.port.AgentContentPort;
import com.weedrice.whiteboard.domain.agent.integration.AgentBoardAccessService;
import com.weedrice.whiteboard.domain.agent.integration.AgentHomeReadModelService;
import com.weedrice.whiteboard.domain.agent.integration.AgentPostListItemAssembler;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentReadModelAssembler;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSupport;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentQueryServicePageableTest {

    private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

    @Mock private BoardRepository boardRepository;
    @Mock private BoardAiInfoRepository boardAiInfoRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private SanctionRepository sanctionRepository;
    @Mock private AgentQuotaService agentQuotaService;
    @Mock private PostService postService;
    @Mock private UserBlockService userBlockService;
    @Mock private AgentOwnershipService agentOwnershipService;
    @Mock private AgentContentPort agentContentPort;
    @Mock private AgentPolicyService agentPolicyService;
    @Mock private AgentBoardListReadService agentBoardListReadService;
    @Mock private AgentBoardAccessService agentBoardAccessService;
    @Mock private AgentPostListItemAssembler agentPostListItemAssembler;
    @Mock private AgentNoteService agentNoteService;
    private CommentReadSupport commentReadSupport;
    private AgentHomeReadModelService agentHomeReadModelService;
    private AgentHomeResponseAssembler agentHomeResponseAssembler;

    private AgentQueryService agentQueryService;
    private User user;
    private Agent agent;
    private Board board;
    private Post post;

    @BeforeEach
    void setUp() {
        agentHomeReadModelService = mock(AgentHomeReadModelService.class);
        agentHomeResponseAssembler = new AgentHomeResponseAssembler();
        agentQueryService = new AgentQueryService(
                agentRepository,
                agentOwnershipService,
                agentBoardListReadService,
                agentPolicyService,
                agentHomeReadModelService,
                agentHomeResponseAssembler,
                agentContentPort);

        user = User.builder().loginId("user").displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 7L);

        board = Board.builder().boardName("Free").boardUrl("free").creator(user).build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        post = Post.builder().board(board).user(user).title("title").contents("content").build();
        ReflectionTestUtils.setField(post, "postId", 100L);
    }

    @Test
    void getFeed_limitsSizeAndForcesCreatedAtDescPostIdDesc() {
        when(agentOwnershipService.resolveActiveAgent(7L)).thenReturn(agent);
        when(agentContentPort.getFeed(eq(agent), isNull(), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(2)));

        agentQueryService.getFeed(7L, null, PageRequest.of(3, 100, Sort.by("likeCount")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(agentContentPort).getFeed(eq(agent), isNull(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId")));
    }

    @Test
    void getMyPosts_limitsSizeAndIgnoresInvalidSort() {
        when(agentOwnershipService.resolveActiveAgent(7L)).thenReturn(agent);
        when(agentContentPort.getMyPosts(eq(agent), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));

        agentQueryService.getMyPosts(7L, PageRequest.of(1, 100, Sort.by("displayName")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(agentContentPort).getMyPosts(eq(agent), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void getBoardPosts_limitsSizeAndKeepsAllowedSortOnly() {
        when(agentOwnershipService.resolveActiveAgent(7L)).thenReturn(agent);
        when(agentContentPort.getBoardPosts(eq(agent), eq(10L), eq(9L), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(3)));

        Sort requestedSort = Sort.by(Sort.Order.asc("displayName"), Sort.Order.desc("likeCount"));
        agentQueryService.getBoardPosts(7L, 10L, 9L, PageRequest.of(2, 100, requestedSort));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(agentContentPort).getBoardPosts(eq(agent), eq(10L), eq(9L), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("likeCount")));
    }

    @Test
    void getPostComments_limitsSizeAndForcesCreatedAtAscCommentIdAsc() {
        when(agentOwnershipService.resolveActiveAgent(7L)).thenReturn(agent);
        when(agentContentPort.getPostComments(eq(agent), eq(100L), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(2)));

        Page<AgentCommentItem> response = agentQueryService.getPostComments(
                7L,
                100L,
                PageRequest.of(4, 100, Sort.by(Sort.Direction.DESC, "likeCount")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(agentContentPort).getPostComments(eq(agent), eq(100L), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(4);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId")));
        assertThat(response.getPageable()).isEqualTo(pageable);
    }
}
