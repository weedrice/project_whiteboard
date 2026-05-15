package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentClaimRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentDailyQuota;
import com.weedrice.whiteboard.domain.agent.repository.AgentDailyQuotaRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentCreateContext;
import com.weedrice.whiteboard.domain.comment.service.CommentReadModelAssembler;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSupport;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.service.PostCreateContext;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private AgentAuditLogWriter agentAuditLogWriter;
    @Mock
    private AgentDailyQuotaRepository agentDailyQuotaRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockService userBlockService;
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
    @Mock
    private SanctionService sanctionService;
    @Mock
    private SanctionPolicyService sanctionPolicyService;
    @Mock
    private EntityManager entityManager;

    private AgentOwnershipService agentOwnershipService;
    private AgentAuditService agentAuditService;
    private AgentBoardAccessService agentBoardAccessService;
    private AgentPostListItemAssembler agentPostListItemAssembler;
    private CommentReadSupport commentReadSupport;
    private AgentLifecycleService agentLifecycleService;
    private AgentAuthService agentAuthService;
    private AgentQueryService agentQueryService;
    private AgentQuotaService agentQuotaService;
    private AgentCommandService agentCommandService;

    private User user;
    private Agent agent;
    private Board writableBoard;
    private Board blockedBoard;
    private Post writablePost;
    private Post blockedPost;

    @BeforeEach
    void setUp() {
        agentOwnershipService = spy(new AgentOwnershipService(agentRepository, sanctionService));
        agentAuditService = spy(new AgentAuditService(agentAuditLogWriter));
        agentBoardAccessService = spy(new AgentBoardAccessService(
                adminRepository,
                boardRepository,
                boardCategoryRepository,
                postService));
        agentPostListItemAssembler = spy(new AgentPostListItemAssembler(commentRepository));
        commentReadSupport = new CommentReadSupport(commentRepository);
        CommentReadModelAssembler commentReadModelAssembler = new CommentReadModelAssembler(commentReadSupport);
        agentQuotaService = new AgentQuotaService(agentDailyQuotaRepository);
        agentLifecycleService = new AgentLifecycleService(
                agentRepository,
                userRepository,
                agentAuditService,
                sanctionPolicyService,
                entityManager);
        agentAuthService = new AgentAuthService(agentRepository, agentOwnershipService);
        PostAccessPolicy postAccessPolicy = new PostAccessPolicy(new BoardAccessPolicy(adminRepository));
        agentQueryService = new AgentQueryService(
                boardRepository,
                boardAiInfoRepository,
                postRepository,
                commentRepository,
                postService,
                postAccessPolicy,
                userBlockService,
                agentOwnershipService,
                agentBoardAccessService,
                agentPostListItemAssembler,
                commentReadSupport,
                commentReadModelAssembler);
        AgentLinkBuilder agentLinkBuilder = new AgentLinkBuilder();
        ReflectionTestUtils.setField(agentLinkBuilder, "frontendUrl", "https://noviis.kr");
        agentCommandService = new AgentCommandService(
                boardRepository,
                boardCategoryRepository,
                commentRepository,
                postService,
                commentService,
                agentOwnershipService,
                agentBoardAccessService,
                agentAuditService,
                agentQuotaService,
                agentLinkBuilder);

        lenient().when(agentDailyQuotaRepository.findForUpdate(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(agentDailyQuotaRepository.saveAndFlush(any(AgentDailyQuota.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(any(), anyLong()))
                .thenReturn(List.of());
        lenient().when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        lenient().when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(any(), eq(true)))
                .thenReturn(List.of());
        lenient().doAnswer(invocation -> {
            User targetUser = invocation.getArgument(0);
            if (targetUser == null || !"ACTIVE".equals(targetUser.getStatus())) {
                throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
            }
            return null;
        }).when(sanctionService).validateNotBanned(any());
        lenient().doAnswer(invocation -> {
            User targetUser = invocation.getArgument(0);
            if (targetUser == null || !"ACTIVE".equals(targetUser.getStatus())) {
                throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
            }
            return null;
        }).when(sanctionPolicyService).validateNotBanned(any());

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
                .thenAnswer(invocation -> {
                    User adminUser = invocation.getArgument(0);
                    Collection<?> boardIds = invocation.getArgument(1);
                    if (adminUser == user && boardIds != null && boardIds.contains(10L)) {
                        return List.of(activeAdmin(user, writableBoard));
                    }
                    return List.of();
                });
    }

    @Test
    @DisplayName("feed는 agent가 글을 쓸 수 없는 게시판의 글을 제외한다")
    void getFeed_filtersBoardsWithoutWritePermission() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(writableBoard, blockedBoard));
        when(postRepository.findAgentFeedByBoardIds(
                eq(List.of(10L)),
                any(),
                any(),
                eq(1L),
                any()))
                .thenReturn(new PageImpl<>(List.of(writablePost), PageRequest.of(0, 10), 1));
        when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(List.of(100L), 7L))
                .thenReturn(List.of());

        Page<AgentPostListItem> response = agentQueryService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getBoardId()).isEqualTo(10L);
        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(commentRepository).findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(List.of(100L), 7L);
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getFeed_excludesReadableOnlyBoardWhenBoardListUsesWritablePolicy() {
        Board readableOnlyBoard = readableOnlyAgentEnabledBoard(30L);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(writableBoard, readableOnlyBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(10L, 30L), true)).thenReturn(List.of(
                        defaultCategory(writableBoard, Role.BOARD_ADMIN),
                        defaultCategory(readableOnlyBoard, Role.BOARD_ADMIN)));
        when(postRepository.findAgentFeedByBoardIds(
                eq(List.of(10L)),
                any(),
                any(),
                eq(1L),
                any()))
                .thenReturn(new PageImpl<>(List.of(writablePost), PageRequest.of(0, 10), 1));

        Page<AgentPostListItem> response = agentQueryService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getBoardId()).isEqualTo(10L);
        verify(postRepository).findAgentFeedByBoardIds(eq(List.of(10L)), any(), any(), eq(1L), any());
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getFeed_keepsLegacyUnknownMinWriteRoleLenientForBoardList() {
        Board legacyBoard = readableOnlyAgentEnabledBoard(30L);
        BoardCategory legacyCategory = defaultCategory(legacyBoard, Role.USER);
        ReflectionTestUtils.setField(legacyCategory, "minWriteRole", "LEGACY");

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(legacyBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(30L), true)).thenReturn(List.of(legacyCategory));
        when(postRepository.findAgentFeedByBoardIds(
                eq(List.of(30L)),
                any(),
                any(),
                eq(1L),
                any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        Page<AgentPostListItem> response = agentQueryService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).isEmpty();
        verify(postRepository).findAgentFeedByBoardIds(eq(List.of(30L)), any(), any(), eq(1L), any());
    }

    @Test
    void claim_requiresVerifiedEmail() {
        ReflectionTestUtils.setField(user, "isEmailVerified", false);

        AgentClaimRequest request = new AgentClaimRequest();
        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_token");

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentLifecycleService.claim(1L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);

        verify(agentRepository, never()).findByAgentTokenHashAndIsDeletedFalseForUpdate(any());
    }

    @Test
    void claim_rejectsSuspendedOwnerBeforeTokenLock() {
        user.suspend();
        AgentClaimRequest request = new AgentClaimRequest();
        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_token");

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentLifecycleService.claim(1L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(agentRepository, never()).findByAgentTokenHashAndIsDeletedFalseForUpdate(any());
    }

    @Test
    void getMyAgents_rejectsDeletedOwner() {
        user.delete();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentLifecycleService.getMyAgents(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(agentRepository, never()).findByUserAndIsDeletedFalseOrderByCreatedAtDesc(any());
    }

    @Test
    void getStatus_usesSameDailyRangeForPostAndCommentCounts() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                eq(7L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(2L);
        when(commentRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                eq(7L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(3L);

        AgentStatusResponse response = agentQueryService.getStatus(7L);

        ArgumentCaptor<LocalDateTime> postStartCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> postEndCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> commentStartCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> commentEndCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(postRepository).countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                eq(7L),
                postStartCaptor.capture(),
                postEndCaptor.capture());
        verify(commentRepository).countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                eq(7L),
                commentStartCaptor.capture(),
                commentEndCaptor.capture());

        assertThat(commentStartCaptor.getValue()).isEqualTo(postStartCaptor.getValue());
        assertThat(commentEndCaptor.getValue()).isEqualTo(postEndCaptor.getValue());
        assertThat(postEndCaptor.getValue()).isEqualTo(postStartCaptor.getValue().plusDays(1));
        assertThat(response.getStats().getPostsToday()).isEqualTo(2L);
        assertThat(response.getStats().getCommentsToday()).isEqualTo(3L);
    }

    @Test
    void register_trimsDescriptionBeforeSave() {
        AgentRegisterRequest request = agentRegisterRequest("  desc  ");

        agentLifecycleService.register(request);

        verify(agentRepository).save(argThat(savedAgent -> "desc".equals(savedAgent.getDescription())));
    }

    @Test
    void register_rejectsNullDescriptionBeforeSave() {
        AgentRegisterRequest request = agentRegisterRequest(null);

        assertThatThrownBy(() -> agentLifecycleService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(agentRepository, never()).save(any());
    }

    @Test
    void register_rejectsBlankDescriptionBeforeSave() {
        AgentRegisterRequest request = agentRegisterRequest("   ");

        assertThatThrownBy(() -> agentLifecycleService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(agentRepository, never()).save(any());
    }

    @Test
    void register_rejectsHtmlDescriptionBeforeSave() {
        AgentRegisterRequest request = agentRegisterRequest("<script>alert(1)</script>");

        assertThatThrownBy(() -> agentLifecycleService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(agentRepository, never()).save(any());
    }

    @Test
    void register_rejectsTooLongDescriptionBeforeSave() {
        AgentRegisterRequest request = agentRegisterRequest("a".repeat(AgentRegisterRequest.DESCRIPTION_MAX_LENGTH + 1));

        assertThatThrownBy(() -> agentLifecycleService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(agentRepository, never()).save(any());
    }

    @Test
    void register_rejectsRawTooLongDescriptionBeforeSave() {
        AgentRegisterRequest request = agentRegisterRequest(
                " " + "a".repeat(AgentRegisterRequest.DESCRIPTION_MAX_LENGTH) + " ");

        assertThatThrownBy(() -> agentLifecycleService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(agentRepository, never()).save(any());
    }

    @Test
    void register_generatesRandomKoreanNicknameWhenNameIsBlank() {
        AgentLifecycleService spyService = spy(agentLifecycleService);
        AgentRegisterRequest request = agentRegisterRequest("desc");

        doReturn("푸른 고래").when(spyService).generateBaseAgentNickname();
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래")).thenReturn(false);

        spyService.register(request);

        verify(agentRepository).save(argThat(savedAgent -> "푸른 고래".equals(savedAgent.getName())));
    }

    @Test
    void generateBaseAgentNickname_dictionaryDoesNotContainMojibakeCharacters() {
        String[] prefixes = (String[]) ReflectionTestUtils.getField(AgentLifecycleService.class, "AGENT_NAME_PREFIXES");
        String[] suffixes = (String[]) ReflectionTestUtils.getField(AgentLifecycleService.class, "AGENT_NAME_SUFFIXES");
        List<String> nameParts = new ArrayList<>();
        Collections.addAll(nameParts, prefixes);
        Collections.addAll(nameParts, suffixes);

        assertThat(nameParts)
                .isNotEmpty()
                .allSatisfy(part -> assertThat(part)
                        .matches("^[가-힣]+$")
                        .doesNotContain("�")
                        .doesNotContain("?"));
    }

    @Test
    void register_appendsSuffixWhenGeneratedNicknameAlreadyExists() {
        AgentLifecycleService spyService = spy(agentLifecycleService);
        AgentRegisterRequest request = agentRegisterRequest("desc");

        doReturn("푸른 고래").when(spyService).generateBaseAgentNickname();
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래")).thenReturn(true);
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래 2")).thenReturn(false);

        spyService.register(request);

        verify(agentRepository).existsByNameAndIsDeletedFalse("푸른 고래");
        verify(agentRepository).existsByNameAndIsDeletedFalse("푸른 고래 2");
        verify(agentRepository).save(argThat(savedAgent -> "푸른 고래 2".equals(savedAgent.getName())));
    }

    @Test
    void register_ignoresRequestedNameAndAlwaysGeneratesNickname() {
        AgentLifecycleService spyService = spy(agentLifecycleService);
        AgentRegisterRequest request = agentRegisterRequest("desc");

        doReturn("푸른 고래").when(spyService).generateBaseAgentNickname();
        when(agentRepository.existsByNameAndIsDeletedFalse("푸른 고래")).thenReturn(false);

        spyService.register(request);

        verify(agentRepository).save(argThat(savedAgent -> "푸른 고래".equals(savedAgent.getName())));
    }

    @Test
    void suspendMyAgent_keepsDisplayFields() {
        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        AgentResponse response = agentLifecycleService.suspendMyAgent(1L, 7L, null);

        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_SUSPENDED);
        assertThat(agent.getName()).isEqualTo("agent");
        assertThat(agent.getDescription()).isEqualTo("desc");
        verify(agentAuditService).saveLog(
                agent,
                user,
                AgentAuditActionType.SUSPEND,
                AgentAuditTargetType.AGENT,
                7L,
                null);
        verify(agentRepository).findByAgentIdForUpdate(7L);
        verify(userRepository).findByIdForUpdate(1L);
        verify(agentRepository, never()).findByAgentIdAndIsDeletedFalse(anyLong());
        InOrder inOrder = inOrder(userRepository, agentRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(agentRepository).findByAgentIdForUpdate(7L);
    }

    @Test
    void suspendMyAgent_rejectsBannedOwner() {
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionPolicyService).validateNotBanned(user);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentLifecycleService.suspendMyAgent(1L, 7L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(agentRepository, never()).findByAgentIdForUpdate(anyLong());
        verify(agentRepository, never()).findByAgentIdAndIsDeletedFalse(anyLong());
    }

    @Test
    void activateMyAgent_reactivatesSuspendedOwnedAgent() {
        agent.suspend();
        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        AgentResponse response = agentLifecycleService.activateMyAgent(1L, 7L, null);

        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_ACTIVE);
        assertThat(agent.isActive()).isTrue();
        verify(agentAuditService).saveLog(
                agent,
                user,
                AgentAuditActionType.REACTIVATE,
                AgentAuditTargetType.AGENT,
                7L,
                null);
        InOrder inOrder = inOrder(userRepository, agentRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(agentRepository).findByAgentIdForUpdate(7L);
    }

    @Test
    void activateMyAgent_returnsActiveAgentWithoutAuditLog() {
        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        AgentResponse response = agentLifecycleService.activateMyAgent(1L, 7L, null);

        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_ACTIVE);
        verify(agentAuditService, never()).saveLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    void activateMyAgent_rejectsPendingClaimStatus() {
        Agent pendingAgent = Agent.builder()
                .user(user)
                .agentTokenHash("pending")
                .name("pending")
                .description("desc")
                .status(Agent.STATUS_PENDING_CLAIM)
                .build();
        ReflectionTestUtils.setField(pendingAgent, "agentId", 9L);
        when(agentRepository.findByAgentIdForUpdate(9L)).thenReturn(Optional.of(pendingAgent));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentLifecycleService.activateMyAgent(1L, 9L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void activateMyAgent_rejectsMissingAgent() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentLifecycleService.activateMyAgent(1L, 99L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AGENT_NOT_FOUND);
    }

    @Test
    void activateMyAgent_rejectsForeignAgent() {
        User otherUser = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        Agent foreignAgent = Agent.builder()
                .user(otherUser)
                .agentTokenHash("foreign")
                .name("foreign")
                .description("desc")
                .status(Agent.STATUS_SUSPENDED)
                .build();
        ReflectionTestUtils.setField(foreignAgent, "agentId", 8L);
        when(agentRepository.findByAgentIdForUpdate(8L)).thenReturn(Optional.of(foreignAgent));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentLifecycleService.activateMyAgent(1L, 8L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    void activateMyAgent_rejectsBannedOwner() {
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionPolicyService).validateNotBanned(user);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentLifecycleService.activateMyAgent(1L, 7L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(agentRepository, never()).findByAgentIdForUpdate(anyLong());
    }

    @Test
    void deleteMyAgent_softDeletesOwnedAgent() {
        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        agentLifecycleService.deleteMyAgent(1L, 7L, null);

        assertThat(agent.getIsDeleted()).isTrue();
        verify(agentAuditService).saveLog(
                agent,
                user,
                AgentAuditActionType.DELETE,
                AgentAuditTargetType.AGENT,
                7L,
                null);
        verify(agentRepository).findByAgentIdForUpdate(7L);
        verify(userRepository).findByIdForUpdate(1L);
        verify(agentRepository, never()).findByAgentIdAndIsDeletedFalse(anyLong());
        InOrder inOrder = inOrder(userRepository, agentRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(agentRepository).findByAgentIdForUpdate(7L);
    }

    @Test
    void deleteMyAgent_rejectsSuspendedOwner() {
        user.suspend();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> agentLifecycleService.deleteMyAgent(1L, 7L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(agentRepository, never()).findByAgentIdForUpdate(anyLong());
        verify(agentRepository, never()).findByAgentIdAndIsDeletedFalse(anyLong());
    }

    @Test
    void suspendAllForUser_locksAgentsInStableOrder() {
        when(agentRepository.findByUserIdAndIsDeletedFalseForUpdateOrderByAgentIdAsc(1L))
                .thenReturn(List.of(agent));

        agentLifecycleService.suspendAllForUser(user);

        assertThat(agent.getStatus()).isEqualTo(Agent.STATUS_SUSPENDED);
        verify(agentRepository).findByUserIdAndIsDeletedFalseForUpdateOrderByAgentIdAsc(1L);
        verify(agentRepository, never()).findByUserAndIsDeletedFalseOrderByCreatedAtDesc(any());
    }

    @Test
    void claim_reactivatesSuspendedAgentForSameUser() {
        agent.suspend();
        AgentLifecycleService spyService = spy(agentLifecycleService);
        AgentClaimRequest request = new AgentClaimRequest();
        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_token");

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalseForUpdate(any())).thenReturn(Optional.of(agent));

        AgentResponse response = spyService.claim(1L, request, null);

        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_ACTIVE);
        assertThat(agent.isActive()).isTrue();
        assertThat(agent.getName()).isEqualTo("agent");
    }

    @Test
    void authenticate_success_usesTokenHashForUpdate() {
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalseForUpdate(any())).thenReturn(Optional.of(agent));

        Agent result = agentAuthService.authenticate("noviis_agt_token");

        assertThat(result).isEqualTo(agent);
        assertThat(agent.getLastUsedAt()).isNotNull();
        verify(agentRepository).findByAgentTokenHashAndIsDeletedFalseForUpdate(any());
        verify(agentRepository, never()).findByAgentTokenHashAndIsDeletedFalse(any());
    }

    @Test
    void authenticate_rejectsSuspendedOwner() {
        user.suspend();
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalseForUpdate(any())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentAuthService.authenticate("noviis_agt_token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    void authenticate_rejectsDeletedOwnerBeforeTouchingLastUsed() {
        user.delete();
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalseForUpdate(any())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentAuthService.authenticate("noviis_agt_token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);
        assertThat(agent.getLastUsedAt()).isNull();
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

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalseForUpdate(any())).thenReturn(Optional.of(pendingAgent));
        when(agentRepository.findByUserIdAndIsDeletedFalseForUpdateOrderByAgentIdAsc(1L))
                .thenReturn(List.of(previousAgent));

        AgentResponse response = agentLifecycleService.claim(1L, request, null);

        assertThat(response.getAgentId()).isEqualTo(9L);
        assertThat(response.getStatus()).isEqualTo(Agent.STATUS_ACTIVE);
        assertThat(previousAgent.getIsDeleted()).isTrue();
        assertThat(pendingAgent.getUser()).isEqualTo(user);
        verify(agentAuditLogWriter).saveLog(
                3L,
                1L,
                AgentAuditActionType.DELETE,
                AgentAuditTargetType.AGENT,
                3L,
                null,
                null);
        verify(agentAuditLogWriter).saveLog(
                9L,
                1L,
                AgentAuditActionType.CLAIM,
                AgentAuditTargetType.AGENT,
                9L,
                null,
                null);
        InOrder inOrder = inOrder(userRepository, agentRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(agentRepository).findByAgentTokenHashAndIsDeletedFalseForUpdate(any());
        inOrder.verify(agentRepository).findByUserIdAndIsDeletedFalseForUpdateOrderByAgentIdAsc(1L);
        verify(agentRepository).findByUserIdAndIsDeletedFalseForUpdateOrderByAgentIdAsc(1L);
    }

    @Test
    void claim_expiredPendingClaim_softDeletesAndReturnsNotFound() {
        Agent pendingAgent = Agent.builder()
                .agentTokenHash("new-hash")
                .name("pending-agent")
                .description("desc")
                .status(Agent.STATUS_PENDING_CLAIM)
                .build();
        ReflectionTestUtils.setField(pendingAgent, "agentId", 9L);
        ReflectionTestUtils.setField(pendingAgent, "createdAt", LocalDateTime.now().minusHours(25));

        AgentClaimRequest request = new AgentClaimRequest();
        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_new");

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(agentRepository.findByAgentTokenHashAndIsDeletedFalseForUpdate(any())).thenReturn(Optional.of(pendingAgent));

        assertThatThrownBy(() -> agentLifecycleService.claim(1L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AGENT_NOT_FOUND);

        assertThat(pendingAgent.getIsDeleted()).isTrue();
        verifyNoInteractions(agentAuditLogWriter);
    }

    @Test
    void cleanupExpiredPendingClaims_softDeletesOnlyExpiredPendingClaims() {
        Agent expiredPendingAgent = Agent.builder()
                .agentTokenHash("expired-pending")
                .name("expired-pending")
                .description("desc")
                .status(Agent.STATUS_PENDING_CLAIM)
                .build();

        when(agentRepository.findExpiredPendingClaimsForUpdate(eq(Agent.STATUS_PENDING_CLAIM), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredPendingAgent));

        int deletedCount = agentLifecycleService.softDeleteExpiredPendingClaims();

        assertThat(deletedCount).isEqualTo(1);
        assertThat(expiredPendingAgent.getIsDeleted()).isTrue();
        verify(agentRepository).findExpiredPendingClaimsForUpdate(eq(Agent.STATUS_PENDING_CLAIM), any(LocalDateTime.class));
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

        var response = agentLifecycleService.getMyAgents(1L);

        assertThat(response.getAgents()).hasSize(1);
        assertThat(response.getAgents().get(0).getAgentId()).isEqualTo(11L);
        verify(agentRepository).findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    }

    @Test
    void getMyPosts_returnsAgentPosts() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.findByAgent_AgentIdAndIsDeleted(eq(7L), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(writablePost), PageRequest.of(0, 10), 1));

        Page<AgentPostListItem> response = agentQueryService.getMyPosts(7L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPostId()).isEqualTo(100L);
        verify(postRepository).findByAgent_AgentIdAndIsDeleted(eq(7L), eq(false), any());
    }

    @Test
    void getBoards_returnsOnlyWritableAgentEnabledBoards() {
        BoardAiInfo boardAiInfo = BoardAiInfo.builder()
                .board(writableBoard)
                .guidePrompt("prompt")
                .build();
        ReflectionTestUtils.setField(boardAiInfo, "boardId", 10L);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(writableBoard, blockedBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(10L), true)).thenReturn(List.of(defaultCategory(writableBoard, Role.BOARD_ADMIN)));
        when(boardAiInfoRepository.findByBoard_BoardIdIn(List.of(10L))).thenReturn(List.of(boardAiInfo));
        when(postRepository.countActiveByBoardIds(List.of(10L)))
                .thenReturn(List.of(boardPostCount(10L, 7L)));

        AgentBoardListResponse response = agentQueryService.getBoards(7L);

        assertThat(response.getBoards()).hasSize(1);
        assertThat(response.getBoards().get(0).getBoardId()).isEqualTo(10L);
        assertThat(response.getBoards().get(0).getGuidePrompt()).isEqualTo("prompt");
        assertThat(response.getBoards().get(0).getPostCount()).isEqualTo(7L);
        verify(boardCategoryRepository).findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(10L), true);
        verify(boardAiInfoRepository).findByBoard_BoardIdIn(List.of(10L));
        verify(postRepository).countActiveByBoardIds(List.of(10L));
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getBoards_excludesReadableOnlyBoardWhenBoardListUsesWritablePolicy() {
        Board readableOnlyBoard = readableOnlyAgentEnabledBoard(30L);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(writableBoard, readableOnlyBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(10L, 30L), true)).thenReturn(List.of(
                        defaultCategory(writableBoard, Role.BOARD_ADMIN),
                        defaultCategory(readableOnlyBoard, Role.BOARD_ADMIN)));
        when(boardAiInfoRepository.findByBoard_BoardIdIn(List.of(10L))).thenReturn(List.of());
        when(postRepository.countActiveByBoardIds(List.of(10L)))
                .thenReturn(List.of(boardPostCount(10L, 7L)));

        AgentBoardListResponse response = agentQueryService.getBoards(7L);

        assertThat(response.getBoards()).hasSize(1);
        assertThat(response.getBoards().get(0).getBoardId()).isEqualTo(10L);
        verify(boardAiInfoRepository).findByBoard_BoardIdIn(List.of(10L));
        verify(postRepository).countActiveByBoardIds(List.of(10L));
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
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(managedBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(30L), true)).thenReturn(List.of(defaultCategory(managedBoard, Role.BOARD_ADMIN)));
        when(boardAiInfoRepository.findByBoard_BoardIdIn(List.of(30L))).thenReturn(List.of());
        when(postRepository.countActiveByBoardIds(List.of(30L))).thenReturn(List.of(boardPostCount(30L, 2L)));
        when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(user, List.of(30L), true))
                .thenReturn(List.of(activeAdmin(user, managedBoard)));

        AgentBoardListResponse response = agentQueryService.getBoards(7L);

        assertThat(response.getBoards()).hasSize(1);
        assertThat(response.getBoards().get(0).getBoardId()).isEqualTo(30L);
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getBoards_queriesMetadataOnlyForWritableBoardsAfterRoleFiltering() {
        User otherUser = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);

        Board superAdminOnlyBoard = Board.builder().boardName("Super").boardUrl("super").creator(otherUser).build();
        ReflectionTestUtils.setField(superAdminOnlyBoard, "boardId", 40L);
        ReflectionTestUtils.setField(superAdminOnlyBoard, "isActive", true);
        ReflectionTestUtils.setField(superAdminOnlyBoard, "isPublic", true);
        ReflectionTestUtils.setField(superAdminOnlyBoard, "agentUseYn", true);

        BoardAiInfo boardAiInfo = BoardAiInfo.builder()
                .board(writableBoard)
                .guidePrompt("prompt")
                .build();
        ReflectionTestUtils.setField(boardAiInfo, "boardId", 10L);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(writableBoard, superAdminOnlyBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(10L, 40L), true)).thenReturn(List.of(
                        defaultCategory(writableBoard, Role.BOARD_ADMIN),
                        defaultCategory(superAdminOnlyBoard, Role.SUPER_ADMIN)));
        when(boardAiInfoRepository.findByBoard_BoardIdIn(List.of(10L))).thenReturn(List.of(boardAiInfo));
        when(postRepository.countActiveByBoardIds(List.of(10L))).thenReturn(List.of(boardPostCount(10L, 7L)));

        AgentBoardListResponse response = agentQueryService.getBoards(7L);

        assertThat(response.getBoards()).hasSize(1);
        assertThat(response.getBoards().get(0).getBoardId()).isEqualTo(10L);
        verify(boardCategoryRepository).findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(10L, 40L), true);
        verify(boardAiInfoRepository).findByBoard_BoardIdIn(List.of(10L));
        verify(postRepository).countActiveByBoardIds(List.of(10L));
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
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(superAdminOnlyBoard));
        when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(40L), true)).thenReturn(List.of(defaultCategory(superAdminOnlyBoard, Role.SUPER_ADMIN)));

        AgentBoardListResponse response = agentQueryService.getBoards(7L);

        assertThat(response.getBoards()).isEmpty();
        verify(boardAiInfoRepository, never()).findByBoard_BoardIdIn(any());
        verify(postRepository, never()).countActiveByBoardIds(any());
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getBoards_returnsEmptyWithoutMetadataWhenNoAgentEnabledBoards() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(blockedBoard));

        AgentBoardListResponse response = agentQueryService.getBoards(7L);

        assertThat(response.getBoards()).isEmpty();
        verify(boardCategoryRepository, never()).findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                any(), eq(true));
        verify(boardAiInfoRepository, never()).findByBoard_BoardIdIn(any());
        verify(postRepository, never()).countActiveByBoardIds(any());
        verify(adminRepository, never()).findByUserAndBoard_BoardIdInAndIsActive(any(), any(), eq(true));
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
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(writableBoard));
        when(postRepository.findAgentFeedByBoardIds(
                eq(List.of(10L)),
                any(),
                any(),
                eq(1L),
                any()))
                .thenReturn(new PageImpl<>(List.of(writablePost, secondWritablePost), PageRequest.of(0, 10), 2));
        when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(
                List.of(100L, 101L), 7L)).thenReturn(List.of());

        Page<AgentPostListItem> response = agentQueryService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(2);
        verify(postService, never()).canWriteToBoard(anyLong(), any());
        verify(boardCategoryRepository).findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                List.of(10L), true);
    }

    @Test
    void getFeed_returnsEmptyPageWhenRequestedBoardIsNotAccessible() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardId(20L)).thenReturn(Optional.of(blockedBoard));

        Page<AgentPostListItem> response = agentQueryService.getFeed(7L, 20L, PageRequest.of(0, 10));

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        verify(postRepository, never()).findAgentFeedByBoardIds(any(), any(), any(), anyLong(), any());
    }

    @Test
    void getFeed_returnsPostsWhenRequestedBoardIsAccessible() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardId(10L)).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(postRepository.findAgentFeedByBoardIds(
                eq(List.of(10L)),
                any(),
                any(),
                eq(1L),
                any()))
                .thenReturn(new PageImpl<>(List.of(writablePost), PageRequest.of(0, 10), 1));
        when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(List.of(100L), 7L))
                .thenReturn(List.of());

        Page<AgentPostListItem> response = agentQueryService.getFeed(7L, 10L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getBoardId()).isEqualTo(10L);
        verify(postService).canWriteToBoard(1L, writableBoard);
    }

    @Test
    @DisplayName("feed 조회는 가시성 조건이 반영된 전용 쿼리를 사용한다")
    void getFeed_usesVisibilityAwareFeedQuery() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true))
                .thenReturn(List.of(writableBoard));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(2L));
        when(postRepository.findAgentFeedByBoardIds(
                eq(List.of(10L)),
                eq(List.of(2L)),
                any(),
                eq(1L),
                any()))
                .thenReturn(new PageImpl<>(List.of(writablePost), PageRequest.of(0, 10), 1));
        when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(List.of(100L), 7L))
                .thenReturn(List.of());

        Page<AgentPostListItem> response = agentQueryService.getFeed(7L, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPostId()).isEqualTo(100L);
        assertThat(response.getTotalElements()).isEqualTo(1L);
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getBoardPosts_forbiddenWhenBoardIsNotReadable() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardId(20L)).thenReturn(Optional.of(blockedBoard));

        assertThatThrownBy(() -> agentQueryService.getBoardPosts(7L, 20L, null, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    void getBoardPosts_returnsAgentPostContract() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardId(10L)).thenReturn(Optional.of(writableBoard));
        when(postService.getPosts(eq(10L), eq(9L), isNull(), isNull(), eq(1L), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(writablePost), PageRequest.of(0, 10), 1));
        when(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(List.of(100L), 7L))
                .thenReturn(List.of(100L));

        Page<AgentPostListItem> response = agentQueryService.getBoardPosts(7L, 10L, 9L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPostId()).isEqualTo(100L);
        assertThat(response.getContent().get(0).isHasMyComment()).isTrue();
        assertThat(response.getContent().get(0).getBoardUrl()).isEqualTo("free");
        verify(postService).getPosts(
                10L,
                9L,
                null,
                null,
                1L,
                true,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getBoardPosts_excludesOtherSecretPostsForNormalReadableAgent() {
        User otherUser = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);

        Board normalReadableBoard = Board.builder()
                .boardName("Normal")
                .boardUrl("normal")
                .creator(otherUser)
                .build();
        ReflectionTestUtils.setField(normalReadableBoard, "boardId", 30L);
        ReflectionTestUtils.setField(normalReadableBoard, "isActive", true);
        ReflectionTestUtils.setField(normalReadableBoard, "isPublic", true);
        ReflectionTestUtils.setField(normalReadableBoard, "agentUseYn", true);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardId(30L)).thenReturn(Optional.of(normalReadableBoard));
        when(postService.getPosts(eq(30L), isNull(), isNull(), isNull(), eq(1L), eq(false), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        agentQueryService.getBoardPosts(7L, 30L, null, PageRequest.of(0, 10));

        verify(postService).getPosts(
                30L,
                null,
                null,
                null,
                1L,
                false,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getPostComments_forbiddenWhenPostBoardIsNotReadable() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.findByIdWithRelations(200L)).thenReturn(Optional.of(blockedPost));

        assertThatThrownBy(() -> agentQueryService.getPostComments(7L, 200L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    void getPostComments_allowsReadableOnlyBoardWithoutWritePermission() {
        Board readableOnlyBoard = readableOnlyAgentEnabledBoard(30L);
        Post readableOnlyPost = Post.builder()
                .board(readableOnlyBoard)
                .user(user)
                .title("Readable")
                .contents("content")
                .build();
        ReflectionTestUtils.setField(readableOnlyPost, "postId", 300L);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.findByIdWithRelations(300L)).thenReturn(Optional.of(readableOnlyPost));
        Pageable commentsPageable = agentCommentReadPageable(0, 10);
        when(commentRepository.findParentsWithChildrenOrNotDeleted(300L, true, NO_BLOCKED_USER_IDS, commentsPageable))
                .thenReturn(Page.empty(commentsPageable));

        Page<AgentCommentItem> response = agentQueryService.getPostComments(7L, 300L, PageRequest.of(0, 10));

        assertThat(response.getContent()).isEmpty();
        verify(commentRepository).findParentsWithChildrenOrNotDeleted(300L, true, NO_BLOCKED_USER_IDS, commentsPageable);
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void getPostComments_deletedPost_returnsPostNotFound() {
        ReflectionTestUtils.setField(writablePost, "isDeleted", true);
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(writablePost));

        assertThatThrownBy(() -> agentQueryService.getPostComments(7L, 100L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(commentRepository, never()).findParentsWithChildrenOrNotDeleted(anyLong(), anyBoolean(), any(), any());
    }

    @Test
    void getPostComments_blockedPostAuthor_returnsPostNotFound() {
        User postAuthor = User.builder().loginId("post-author").displayName("Post Author").build();
        ReflectionTestUtils.setField(postAuthor, "userId", 2L);
        Post blockedAuthorPost = Post.builder()
                .board(writableBoard)
                .user(postAuthor)
                .title("Blocked author post")
                .contents("content")
                .build();
        ReflectionTestUtils.setField(blockedAuthorPost, "postId", 400L);
        ReflectionTestUtils.setField(blockedAuthorPost, "isDeleted", false);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.findByIdWithRelations(400L)).thenReturn(Optional.of(blockedAuthorPost));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(2L));

        assertThatThrownBy(() -> agentQueryService.getPostComments(7L, 400L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(commentRepository, never()).findParentsWithChildrenOrNotDeleted(anyLong(), anyBoolean(), any(), any());
    }

    @Test
    void getPostComments_masksBlockedAuthorAndDeletedStatus() {
        User blockedUser = User.builder().loginId("blocked").displayName("Blocked").build();
        ReflectionTestUtils.setField(blockedUser, "userId", 2L);

        Comment blockedComment = Comment.builder()
                .post(writablePost)
                .user(blockedUser)
                .content("blocked content")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(blockedComment, "commentId", 301L);
        ReflectionTestUtils.setField(blockedComment, "likeCount", 3);

        Comment deletedComment = Comment.builder()
                .post(writablePost)
                .user(user)
                .content("deleted content")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(deletedComment, "commentId", 302L);
        ReflectionTestUtils.setField(deletedComment, "isDeleted", true);

        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(writablePost));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(2L));
        Pageable commentsPageable = agentCommentReadPageable(0, 10);
        when(commentRepository.findParentsWithChildrenOrNotDeleted(100L, false, List.of(2L), commentsPageable))
                .thenReturn(new PageImpl<>(List.of(blockedComment, deletedComment), commentsPageable, 2));
        when(commentRepository.countVisibleRepliesByParentIds(List.of(301L, 302L), false, List.of(2L)))
                .thenReturn(List.of(replyCount(301L, 1L)));

        Page<AgentCommentItem> response = agentQueryService.getPostComments(7L, 100L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(2);
        AgentCommentItem blockedItem = response.getContent().get(0);
        AgentCommentItem deletedItem = response.getContent().get(1);

        assertThat(blockedItem.getStatus()).isEqualTo(AgentCommentItem.STATUS_BLOCKED_AUTHOR);
        assertThat(blockedItem.getContent()).isNull();
        assertThat(blockedItem.getAuthor()).isNull();
        assertThat(blockedItem.getReplyCount()).isEqualTo(1L);
        assertThat(blockedItem.isHasReplies()).isTrue();

        assertThat(deletedItem.getStatus()).isEqualTo(AgentCommentItem.STATUS_DELETED);
        assertThat(deletedItem.getContent()).isNull();
        assertThat(deletedItem.getAuthor()).isNull();
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void createPost_forbiddenWhenReadableBoardIsNotWritable() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrlForUpdate("free")).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(false);

        assertThatThrownBy(() -> agentCommandService.createPost(7L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(postService).canWriteToBoard(1L, writableBoard);
        verify(agentDailyQuotaRepository, never()).findForUpdate(anyLong(), any(LocalDate.class), anyString());
        verify(postService, never()).createPostAsAgent(
                anyLong(),
                anyLong(),
                any(PostCreateRequest.class),
                any(PostCreateContext.class));
    }

    @Test
    void createComment_forbiddenWhenReadableBoardIsNotWritable() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "b".repeat(25));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(postService.getPostById(100L, 1L, false)).thenReturn(writablePost);
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(false);

        assertThatThrownBy(() -> agentCommandService.createComment(7L, 100L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(postService).canWriteToBoard(1L, writableBoard);
        verify(agentDailyQuotaRepository, never()).findForUpdate(anyLong(), any(LocalDate.class), anyString());
        verify(commentService, never()).createCommentAsAgent(anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void likePost_forbiddenWhenReadableBoardIsNotWritable() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postService.getPostById(100L, 1L, false)).thenReturn(writablePost);
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(false);

        assertThatThrownBy(() -> agentCommandService.likePost(7L, 100L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(postService).canWriteToBoard(1L, writableBoard);
        verify(postService, never()).likePost(anyLong(), (Agent) any(), any(Post.class));
        verify(agentAuditLogWriter, never()).saveLog(anyLong(), anyLong(), any(), any(), anyLong(),
                any(), any());
    }

    @Test
    @DisplayName("agent post creation is limited to 50 items per day")
    void createPost_dailyLimitExceeded() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrlForUpdate("free")).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(agentDailyQuotaRepository.findForUpdate(eq(7L), any(LocalDate.class), eq("POST")))
                .thenReturn(Optional.of(quota("POST", 50L)));

        assertThatThrownBy(() -> agentCommandService.createPost(7L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);

        verify(postService, never()).createPostAsAgent(
                anyLong(),
                anyLong(),
                any(PostCreateRequest.class),
                any(PostCreateContext.class));
    }

    @Test
    @DisplayName("agent comment creation is limited to 100 items per day")
    void createComment_dailyLimitExceeded() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "b".repeat(25));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(postService.getPostById(100L, 1L, false)).thenReturn(writablePost);
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(agentDailyQuotaRepository.findForUpdate(eq(7L), any(LocalDate.class), eq("COMMENT")))
                .thenReturn(Optional.of(quota("COMMENT", 100L)));

        assertThatThrownBy(() -> agentCommandService.createComment(7L, 100L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);

        verify(commentService, never()).createCommentAsAgent(anyLong(), anyLong(), eq(100L), isNull(), any(), any());
    }

    @Test
    @DisplayName("agent post creation succeeds when the daily limit is not reached")
    void createPost_withinDailyLimit_success() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrlForUpdate("free")).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(agentDailyQuotaRepository.findForUpdate(eq(7L), any(LocalDate.class), eq("POST")))
                .thenReturn(Optional.of(quota("POST", 49L)));
        when(postService.createPostAsAgent(
                eq(1L),
                eq(7L),
                any(PostCreateRequest.class),
                any(PostCreateContext.class))).thenReturn(100L);

        var response = agentCommandService.createPost(7L, request, null);

        assertThat(response.getPostId()).isEqualTo(100L);
        verify(agentRepository).findByAgentIdForUpdate(7L);
        verify(postService).createPostAsAgent(
                eq(1L),
                eq(7L),
                any(PostCreateRequest.class),
                any(PostCreateContext.class));
    }

    @Test
    void createPost_withExplicitCategoryPassesPrevalidatedContext() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "categoryId", 11L);
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        BoardCategory category = BoardCategory.builder()
                .board(writableBoard)
                .name("Open")
                .sortOrder(1)
                .minWriteRole(Role.USER)
                .isDefault(false)
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 11L);

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrlForUpdate("free")).thenReturn(Optional.of(writableBoard));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(11L, 10L, true))
                .thenReturn(Optional.of(category));
        when(agentDailyQuotaRepository.findForUpdate(eq(7L), any(LocalDate.class), eq("POST")))
                .thenReturn(Optional.of(quota("POST", 0L)));
        when(postService.createPostAsAgent(
                eq(1L),
                eq(7L),
                any(PostCreateRequest.class),
                any(PostCreateContext.class))).thenReturn(100L);

        var response = agentCommandService.createPost(7L, request, null);

        assertThat(response.getPostId()).isEqualTo(100L);
        ArgumentCaptor<PostCreateRequest> requestCaptor = ArgumentCaptor.forClass(PostCreateRequest.class);
        ArgumentCaptor<PostCreateContext> contextCaptor = ArgumentCaptor.forClass(PostCreateContext.class);
        verify(postService).createPostAsAgent(
                eq(1L),
                eq(7L),
                requestCaptor.capture(),
                contextCaptor.capture());
        assertThat(requestCaptor.getValue().getCategoryId()).isEqualTo(11L);
        PostCreateContext context = contextCaptor.getValue();
        assertThat(context.agent()).isSameAs(agent);
        assertThat(context.board()).isSameAs(writableBoard);
        assertThat(context.category()).isSameAs(category);
        assertThat(context.boardWritablePrevalidated()).isTrue();
        verify(postService, never()).canWriteToBoard(anyLong(), any());
    }

    @Test
    void createPost_convertsPlainTextLineBreaksToHtmlParagraphs() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "content", "first line\nsecond line\n\nnext paragraph");

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(boardRepository.findByBoardUrlForUpdate("free")).thenReturn(Optional.of(writableBoard));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(agentDailyQuotaRepository.findForUpdate(eq(7L), any(LocalDate.class), eq("POST")))
                .thenReturn(Optional.of(quota("POST", 0L)));
        when(postService.createPostAsAgent(
                eq(1L),
                eq(7L),
                any(PostCreateRequest.class),
                any(PostCreateContext.class))).thenReturn(100L);

        agentCommandService.createPost(7L, request, null);

        verify(postService).createPostAsAgent(
                eq(1L),
                eq(7L),
                argThat(postRequest ->
                        "<p>first line<br>second line</p><p>next paragraph</p>"
                                .equals(postRequest.getContents())),
                any(PostCreateContext.class));
    }

    @Test
    @DisplayName("agent comment creation succeeds when the daily limit is not reached")
    void createComment_withinDailyLimit_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "b".repeat(25));

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(postService.getPostById(100L, 1L, false)).thenReturn(writablePost);
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(agentDailyQuotaRepository.findForUpdate(eq(7L), any(LocalDate.class), eq("COMMENT")))
                .thenReturn(Optional.of(quota("COMMENT", 99L)));
        when(commentService.createCommentAsAgent(eq(1L), eq(7L), eq(100L), isNull(), eq("b".repeat(25)),
                any(CommentCreateContext.class))).thenReturn(300L);

        var response = agentCommandService.createComment(7L, 100L, request, null);

        assertThat(response.getCommentId()).isEqualTo(300L);
        verify(agentRepository).findByAgentIdForUpdate(7L);
        verify(commentService).createCommentAsAgent(eq(1L), eq(7L), eq(100L), isNull(), eq("b".repeat(25)),
                argThat(context -> context.agent() == agent
                        && context.post() == writablePost
                        && context.parentComment() == null
                        && context.postReadablePrevalidated()));
        InOrder inOrder = inOrder(agentDailyQuotaRepository, commentService, agentAuditService);
        inOrder.verify(agentDailyQuotaRepository).findForUpdate(eq(7L), any(LocalDate.class), eq("COMMENT"));
        inOrder.verify(commentService).createCommentAsAgent(eq(1L), eq(7L), eq(100L), isNull(), eq("b".repeat(25)),
                any(CommentCreateContext.class));
        inOrder.verify(agentAuditService).saveLog(
                agent,
                user,
                AgentAuditActionType.CREATE_COMMENT,
                AgentAuditTargetType.COMMENT,
                300L,
                null);
    }

    @Test
    void createReply_withinDailyLimit_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "reply");

        Comment parentComment = Comment.builder().post(writablePost).user(user).content("parent").build();
        ReflectionTestUtils.setField(parentComment, "commentId", 500L);
        ReflectionTestUtils.setField(parentComment, "isDeleted", false);

        when(agentRepository.findByAgentIdForUpdate(7L)).thenReturn(Optional.of(agent));
        when(commentRepository.findByIdWithRelationsForUpdate(500L)).thenReturn(Optional.of(parentComment));
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(agentDailyQuotaRepository.findForUpdate(eq(7L), any(LocalDate.class), eq("COMMENT")))
                .thenReturn(Optional.of(quota("COMMENT", 99L)));
        when(commentService.createCommentAsAgent(eq(1L), eq(7L), eq(100L), eq(500L), eq("reply"),
                any(CommentCreateContext.class))).thenReturn(501L);

        var response = agentCommandService.createReply(7L, 500L, request, null);

        assertThat(response.getCommentId()).isEqualTo(501L);
        verify(agentRepository).findByAgentIdForUpdate(7L);
        verify(commentService).createCommentAsAgent(eq(1L), eq(7L), eq(100L), eq(500L), eq("reply"),
                argThat(context -> context.agent() == agent
                        && context.post() == writablePost
                        && context.parentComment() == parentComment
                        && !context.postReadablePrevalidated()));
        InOrder inOrder = inOrder(agentDailyQuotaRepository, commentService, agentAuditService);
        inOrder.verify(agentDailyQuotaRepository).findForUpdate(eq(7L), any(LocalDate.class), eq("COMMENT"));
        inOrder.verify(commentService).createCommentAsAgent(eq(1L), eq(7L), eq(100L), eq(500L), eq("reply"),
                any(CommentCreateContext.class));
        inOrder.verify(agentAuditService).saveLog(
                agent,
                user,
                AgentAuditActionType.CREATE_COMMENT,
                AgentAuditTargetType.COMMENT,
                501L,
                null);
    }

    @Test
    void likePost_success() {
        when(agentRepository.findByAgentIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(agent));
        when(postService.getPostById(100L, 1L, false)).thenReturn(writablePost);
        when(postService.canWriteToBoard(1L, writableBoard)).thenReturn(true);
        when(postService.likePost(1L, agent, writablePost)).thenReturn(3);

        var response = agentCommandService.likePost(7L, 100L, null);

        assertThat(response.getPostId()).isEqualTo(100L);
        assertThat(response.getLikeCount()).isEqualTo(3);
        assertThat(response.isLiked()).isTrue();
        verify(agentAuditLogWriter).saveLog(
                eq(7L),
                eq(1L),
                eq(AgentAuditActionType.LIKE_POST),
                eq(AgentAuditTargetType.POST),
                eq(100L),
                isNull(), isNull());
    }

    private BoardCategory defaultCategory(Board board, String minWriteRole) {
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("Default")
                .sortOrder(1)
                .minWriteRole(minWriteRole)
                .isDefault(true)
                .build();
        ReflectionTestUtils.setField(category, "categoryId", board.getBoardId() + 1000);
        return category;
    }

    private AgentRegisterRequest agentRegisterRequest(String description) {
        AgentRegisterRequest request = new AgentRegisterRequest();
        ReflectionTestUtils.setField(request, "description", description);
        return request;
    }

    private Pageable agentCommentReadPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId")));
    }

    private Board readableOnlyAgentEnabledBoard(Long boardId) {
        User otherUser = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        Board board = Board.builder()
                .boardName("Readable")
                .boardUrl("readable")
                .creator(otherUser)
                .build();
        ReflectionTestUtils.setField(board, "boardId", boardId);
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);
        ReflectionTestUtils.setField(board, "agentUseYn", true);
        return board;
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

    private CommentRepository.ReplyCountProjection replyCount(Long parentId, long replyCount) {
        return new CommentRepository.ReplyCountProjection() {
            @Override
            public Long getParentId() {
                return parentId;
            }

            @Override
            public long getReplyCount() {
                return replyCount;
            }
        };
    }

    private AgentDailyQuota quota(String actionType, long usedCount) {
        return AgentDailyQuota.builder()
                .agent(agent)
                .quotaDate(LocalDate.now())
                .actionType(actionType)
                .usedCount(usedCount)
                .build();
    }
}
