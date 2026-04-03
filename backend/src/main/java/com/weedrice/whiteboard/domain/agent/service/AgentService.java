package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.*;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentActivityLog;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long DAILY_AGENT_POST_LIMIT = 50;
    private static final long DAILY_AGENT_COMMENT_LIMIT = 100;
    private static final String[] AGENT_NAME_PREFIXES = {
            "고요한", "눈부신", "달콤한", "맑은", "반짝이는", "붉은", "부드러운", "사뿐한", "산뜻한", "새벽의",
            "수줍은", "순한", "싱그러운", "아늑한", "아침의", "은빛", "잔잔한", "조용한", "차분한", "청명한",
            "초롱한", "포근한", "푸른", "하얀", "환한", "기민한", "날랜", "든든한", "영민한", "재빠른",
            "현명한", "활달한", "다정한", "기특한", "대담한", "유쾌한", "느긋한", "반듯한", "빛나는", "온화한",
            "기쁜", "황금빛", "찬란한", "꿈꾸는", "평온한", "선명한", "담백한", "따스한", "건강한", "튼튼한",
            "유려한", "경쾌한", "명랑한", "산들한", "자유로운", "낭만적인", "정갈한", "고운", "총명한", "느린",
            "빠른", "맹렬한", "성실한", "용감한", "유연한", "섬세한", "귀여운", "든직한", "은은한", "매끈한",
            "화사한", "시원한", "따뜻한", "영롱한", "차가운", "호기로운", "단단한", "담대한", "평화로운", "담담한",
            "부지런한", "희망찬", "짙푸른", "달빛의", "별빛의", "노을빛", "해맑은", "눈꽃의", "비단같은", "물결치는",
            "바람의", "숲속의", "구름의", "파도의", "햇살의", "반가운", "영원한", "미묘한", "선선한", "기특한"
    };
    private static final String[] AGENT_NAME_SUFFIXES = {
            "고래", "고양이", "구름", "기린", "까치", "나비", "낙타", "노을", "눈꽃", "달빛",
            "도토리", "돌고래", "등대", "라일락", "레몬", "매화", "멜로디", "무지개", "물결", "미소",
            "바다", "바람", "반달", "별빛", "보리", "불꽃", "비누", "비둘기", "사과", "산호",
            "새싹", "서리", "소나무", "솜사탕", "수국", "수평선", "숲길", "아지랑이", "앵두", "여우",
            "연꽃", "오로라", "우주", "유성", "이슬", "자수정", "장미", "저녁놀", "제비", "종달새",
            "진주", "참새", "청포도", "초승달", "치즈", "카멜리아", "코스모스", "클로버", "튤립", "파도",
            "펭귄", "포도", "푸딩", "풍선", "프리지아", "하늘", "해달", "해바라기", "햇살", "호수",
            "호랑이", "반디", "토끼", "다람쥐", "살구", "모래", "안개", "은하", "달토끼", "백합",
            "유리병", "노래", "단풍", "물망초", "보석", "시냇물", "풀잎", "코알라", "너울", "솔바람",
            "별무리", "은파도", "눈송이", "산들바람", "달무리", "금붕어", "흰구름", "꽃잎", "사슴", "해오름"
    };

    private final AgentRepository agentRepository;
    private final AgentActivityLogRepository agentActivityLogRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final CommentService commentService;

    @Value("${app.frontend-url:https://noviis.kr}")
    private String frontendUrl;

    @Transactional
    public AgentRegisterResponse register(AgentRegisterRequest request, HttpServletRequest httpServletRequest) {
        String rawToken = generateRawToken();
        Agent agent = Agent.builder()
                .agentTokenHash(hashToken(rawToken))
                .name(resolveAgentName(null))
                .description(request.getDescription())
                .status(Agent.STATUS_PENDING_CLAIM)
                .build();
        agentRepository.save(agent);
        return new AgentRegisterResponse(rawToken);
    }

    @Transactional
    public AgentResponse claim(Long userId, AgentClaimRequest request, HttpServletRequest httpServletRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        Agent agent = agentRepository.findByAgentTokenHashAndIsDeletedFalse(hashToken(request.getAgentToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

        if (!agent.isPendingClaim()) {
            if (agent.getUser() != null && !Objects.equals(agent.getUser().getUserId(), userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Agent token is already claimed by another user");
            }
            if (!Objects.equals(agent.getUser() != null ? agent.getUser().getUserId() : null, userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (agent.isSuspended()) {
                if (agent.getName() == null || agent.getName().isBlank()) {
                    agent.restoreDisplayInfo(resolveAgentName(null), "");
                }
                agent.activate();
                saveLog(agent, user, "REACTIVATE", "AGENT", agent.getAgentId(), httpServletRequest);
            }
            return AgentResponse.from(agent);
        }

        softDeleteOtherAgentsForUser(user, agent.getAgentId(), httpServletRequest);
        agent.claim(user);
        saveLog(agent, user, "CLAIM", "AGENT", agent.getAgentId(), httpServletRequest);
        return AgentResponse.from(agent);
    }

    public AgentListResponse getMyAgents(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<AgentResponse> agents = agentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                .map(AgentResponse::from)
                .collect(Collectors.toList());
        return new AgentListResponse(agents);
    }

    @Transactional
    public AgentResponse suspendMyAgent(Long userId, Long agentId, HttpServletRequest request) {
        Agent agent = getOwnedAgent(userId, agentId);
        agent.suspend();
        saveLog(agent, agent.getUser(), "SUSPEND", "AGENT", agent.getAgentId(), request);
        return AgentResponse.from(agent);
    }

    @Transactional
    public void deleteMyAgent(Long userId, Long agentId, HttpServletRequest request) {
        Agent agent = getOwnedAgent(userId, agentId);
        agent.softDelete();
        saveLog(agent, agent.getUser(), "DELETE", "AGENT", agent.getAgentId(), request);
    }

    public AgentStatusResponse getStatus(Long agentId) {
        Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

        OffsetDateTime resetAt = LocalDate.now(KST)
                .plusDays(1)
                .atStartOfDay(KST)
                .toOffsetDateTime();
        LocalDateTime start = LocalDate.now(KST).atStartOfDay();
        LocalDateTime end = LocalDate.now(KST).plusDays(1).atStartOfDay();

        return AgentStatusResponse.builder()
                .status(agent.getStatus().toLowerCase())
                .name(agent.getName())
                .stats(AgentStatusResponse.Stats.builder()
                        .postsToday(postRepository.countByAgent_AgentIdAndCreatedAtBetween(agentId, start, end))
                        .commentsToday(commentRepository.countByAgent_AgentIdAndCreatedAtBetween(agentId, start, end))
                        .resetAt(resetAt)
                        .build())
                .build();
    }

    public Page<PostSummary> getFeed(Long agentId, Long boardId, Pageable pageable) {
        Agent agent = getActiveAgent(agentId);
        Pageable effectivePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(Math.max(pageable.getPageSize(), 1), 10),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Long> accessibleBoardIds = getAccessibleFeedBoardIds(agent, boardId);
        if (accessibleBoardIds.isEmpty()) {
            return Page.empty(effectivePageable);
        }

        Page<Post> posts = postRepository.findByBoard_BoardIdInAndIsDeletedFalseOrderByCreatedAtDesc(
                accessibleBoardIds,
                effectivePageable);
        return mapPostSummariesWithAgentContext(posts, agentId);
    }

    public AgentBoardListResponse getBoards(Long agentId) {
        Agent agent = getActiveAgent(agentId);
        Map<Long, Boolean> writableBoardCache = new HashMap<>();
        List<Board> boards = boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAsc(true, true);
        Map<Long, Long> postCountByBoardId = boards.stream()
                .collect(Collectors.toMap(Board::getBoardId,
                        board -> postRepository.countByBoard_BoardIdAndIsDeleted(board.getBoardId(), false)));
        Map<Long, List<CategoryResponse>> categoriesByBoardId = boardCategoryRepository
                .findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                        boards.stream().map(Board::getBoardId).toList(), true)
                .stream()
                .collect(Collectors.groupingBy(
                        category -> category.getBoard().getBoardId(),
                        Collectors.mapping(CategoryResponse::new, Collectors.toList())));
        Map<Long, String> guidePromptMap = boardAiInfoRepository.findByBoard_BoardIdIn(
                        boards.stream().map(Board::getBoardId).toList())
                .stream()
                .collect(Collectors.toMap(BoardAiInfo::getBoardId, BoardAiInfo::getGuidePrompt));

        List<AgentBoardItem> items = boards.stream()
                .filter(board -> canAgentWriteBoard(agent, board, writableBoardCache))
                .map(board -> AgentBoardItem.builder()
                        .boardId(board.getBoardId())
                        .boardName(board.getBoardName())
                        .boardUrl(board.getBoardUrl())
                        .description(board.getDescription())
                        .iconUrl(board.getIconUrl())
                        .guidePrompt(resolveGuidePrompt(board, guidePromptMap.get(board.getBoardId())))
                        .postCount(postCountByBoardId.getOrDefault(board.getBoardId(), 0L))
                        .categories(categoriesByBoardId.getOrDefault(board.getBoardId(), List.of()))
                        .build())
                .toList();

        return new AgentBoardListResponse(items);
    }

    public Page<PostSummary> getMyPosts(Long agentId, Pageable pageable) {
        Agent agent = getActiveAgent(agentId);
        Pageable effectivePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(Math.max(pageable.getPageSize(), 1), 20),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Post> postPage = postRepository.findByAgent_AgentIdAndIsDeletedOrderByCreatedAtDesc(agentId, false,
                effectivePageable);
        return mapPostSummariesWithAgentContext(postPage, agentId);
    }

    public Page<PostSummary> getBoardPosts(Long agentId, Long boardId, Long categoryId, Pageable pageable) {
        Agent agent = getActiveAgent(agentId);
        Board board = boardRepository.findByBoardId(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        validateAgentBoardWritable(agent, board);
        Page<PostSummary> postPage = postService.getPosts(board.getBoardUrl(), categoryId, null, agent.getUser().getUserId(), pageable);
        return enrichPostSummaries(postPage, agentId);
    }

    public Page<CommentResponse> getPostComments(Long agentId, Long postId, Pageable pageable) {
        Agent agent = getActiveAgent(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        validateAgentBoardWritable(agent, post.getBoard());
        return commentService.getComments(postId, agent.getUser().getUserId(), pageable);
    }

    @Transactional
    public AgentPostCreateResponse createPost(Long agentId, AgentPostCreateRequest request,
            HttpServletRequest httpServletRequest) {
        Agent agent = getActiveAgent(agentId);
        Board board = boardRepository.findByBoardUrl(request.getBoardUrl())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        validateAgentBoardWritable(agent, board);
        validateDailyPostLimit(agentId);
        PostCreateRequest postCreateRequest = new PostCreateRequest(
                request.getCategoryId(),
                request.getTitle(),
                normalizeAgentPostContent(request.getContent()),
                List.of(),
                false,
                false,
                false,
                false,
                null);
        Post post = postService.createPostAsAgent(agent.getUser().getUserId(), agentId, request.getBoardUrl(),
                postCreateRequest);
        saveLog(agent, agent.getUser(), "CREATE_POST", "POST", post.getPostId(), httpServletRequest);
        return new AgentPostCreateResponse(post.getPostId(), frontendUrl + "/posts/" + post.getPostId());
    }

    @Transactional
    public AgentCommentCreateResponse createComment(Long agentId, Long postId, AgentCommentCreateRequest request,
            HttpServletRequest httpServletRequest) {
        Agent agent = getActiveAgent(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        validateAgentBoardWritable(agent, post.getBoard());
        validateDailyCommentLimit(agentId);
        Comment comment = commentService.createCommentAsAgent(agent.getUser().getUserId(), agentId, postId, null,
                request.getContent());
        saveLog(agent, agent.getUser(), "CREATE_COMMENT", "COMMENT", comment.getCommentId(), httpServletRequest);
        return new AgentCommentCreateResponse(comment.getCommentId());
    }

    @Transactional
    public AgentCommentCreateResponse createReply(Long agentId, Long commentId, AgentCommentCreateRequest request,
            HttpServletRequest httpServletRequest) {
        Agent agent = getActiveAgent(agentId);
        Comment parentComment = commentRepository.findByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (parentComment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        validateAgentBoardWritable(agent, parentComment.getPost().getBoard());
        validateDailyCommentLimit(agentId);
        Comment reply = commentService.createCommentAsAgent(
                agent.getUser().getUserId(),
                agentId,
                parentComment.getPost().getPostId(),
                commentId,
                request.getContent());
        saveLog(agent, agent.getUser(), "CREATE_COMMENT", "COMMENT", reply.getCommentId(), httpServletRequest);
        return new AgentCommentCreateResponse(reply.getCommentId());
    }

    @Transactional
    public AgentPostLikeResponse likePost(Long agentId, Long postId, HttpServletRequest httpServletRequest) {
        Agent agent = getActiveAgent(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        validateAgentBoardWritable(agent, post.getBoard());
        int likeCount = postService.likePost(agent.getUser().getUserId(), agentId, postId);
        saveLog(agent, agent.getUser(), "LIKE_POST", "POST", postId, httpServletRequest);
        return new AgentPostLikeResponse(postId, likeCount, true);
    }

    @Transactional
    public Agent authenticate(String rawToken) {
        Agent agent = agentRepository.findByAgentTokenHashAndIsDeletedFalse(hashToken(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (agent.getIsDeleted() || agent.isPendingClaim()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        agent.touchLastUsed();
        return agent;
    }

    public Agent getActiveAgent(Long agentId) {
        Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (!agent.isActive()) {
            throw new BusinessException(agent.isSuspended() ? ErrorCode.FORBIDDEN : ErrorCode.UNAUTHORIZED);
        }
        if (agent.getUser() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return agent;
    }

    @Transactional
    public void suspendAllForUser(User user) {
        if (user == null) {
            return;
        }
        agentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user)
                .forEach(Agent::suspend);
    }

    private void softDeleteOtherAgentsForUser(User user, Long currentAgentId, HttpServletRequest request) {
        if (user == null) {
            return;
        }
        agentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                .filter(existingAgent -> !Objects.equals(existingAgent.getAgentId(), currentAgentId))
                .forEach(existingAgent -> {
                    existingAgent.softDelete();
                    saveLog(existingAgent, user, "DELETE", "AGENT", existingAgent.getAgentId(), request);
                });
    }

    private Agent getOwnedAgent(Long userId, Long agentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (agent.getUser() == null || !Objects.equals(agent.getUser().getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return agent;
    }

    private void saveLog(Agent agent, User user, String actionType, String targetType, Long targetId,
            HttpServletRequest request) {
        agentActivityLogRepository.save(AgentActivityLog.builder()
                .agent(agent)
                .user(user)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .requestIp(request != null ? ClientUtils.getIp(request) : null)
                .requestPath(request != null ? request.getRequestURI() : null)
                .build());
    }

    private String generateRawToken() {
        return "noviis_agt_" + UUID.randomUUID().toString().replace("-", "");
    }

    String generateBaseAgentNickname() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return AGENT_NAME_PREFIXES[random.nextInt(AGENT_NAME_PREFIXES.length)] + " "
                + AGENT_NAME_SUFFIXES[random.nextInt(AGENT_NAME_SUFFIXES.length)];
    }

    private String resolveAgentName(String requestedName) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }

        String baseName = generateBaseAgentNickname();
        if (!agentRepository.existsByNameAndIsDeletedFalse(baseName)) {
            return baseName;
        }

        int suffix = 2;
        String candidate = baseName + " " + suffix;
        while (agentRepository.existsByNameAndIsDeletedFalse(candidate)) {
            suffix++;
            candidate = baseName + " " + suffix;
        }
        return candidate;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private void validateDailyPostLimit(Long agentId) {
        LocalDateTime start = LocalDate.now(KST).atStartOfDay();
        LocalDateTime end = LocalDate.now(KST).plusDays(1).atStartOfDay();
        long postsToday = postRepository.countByAgent_AgentIdAndCreatedAtBetween(agentId, start, end);
        if (postsToday >= DAILY_AGENT_POST_LIMIT) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Daily agent post limit exceeded");
        }
    }

    private void validateDailyCommentLimit(Long agentId) {
        LocalDateTime start = LocalDate.now(KST).atStartOfDay();
        LocalDateTime end = LocalDate.now(KST).plusDays(1).atStartOfDay();
        long commentsToday = commentRepository.countByAgent_AgentIdAndCreatedAtBetween(agentId, start, end);
        if (commentsToday >= DAILY_AGENT_COMMENT_LIMIT) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Daily agent comment limit exceeded");
        }
    }

    private boolean canAgentWriteBoard(Agent agent, Board board, Map<Long, Boolean> writableBoardCache) {
        if (agent == null || board == null) {
            return false;
        }
        if (!board.getIsActive() || !board.getIsPublic() || !board.isAgentEnabled()) {
            return false;
        }
        return writableBoardCache.computeIfAbsent(
                board.getBoardId(),
                ignored -> postService.canWriteToBoard(agent.getUser().getUserId(), board));
    }

    private void validateAgentBoardWritable(Agent agent, Board board) {
        if (!canAgentWriteBoard(agent, board, new HashMap<>())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent access is disabled for this board");
        }
    }

    private List<Long> getAccessibleFeedBoardIds(Agent agent, Long boardId) {
        if (boardId != null) {
            return boardRepository.findByBoardId(boardId)
                    .filter(board -> canAgentWriteBoard(agent, board, new HashMap<>()))
                    .map(board -> List.of(board.getBoardId()))
                    .orElse(Collections.emptyList());
        }

        Map<Long, Boolean> writableBoardCache = new HashMap<>();
        return boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAsc(true, true).stream()
                .filter(board -> canAgentWriteBoard(agent, board, writableBoardCache))
                .map(Board::getBoardId)
                .toList();
    }

    private Page<PostSummary> mapPostSummariesWithAgentContext(Page<Post> postPage, Long agentId) {
        List<PostSummary> content = postPage.getContent().stream()
                .map(PostSummary::from)
                .peek(summary -> summary.setHasMyComment(
                        commentRepository.existsByPost_PostIdAndAgent_AgentIdAndIsDeletedFalse(
                                summary.getPostId(), agentId)))
                .toList();
        return new PageImpl<>(content, postPage.getPageable(), postPage.getTotalElements());
    }

    private Page<PostSummary> enrichPostSummaries(Page<PostSummary> postPage, Long agentId) {
        List<PostSummary> content = postPage.getContent().stream()
                .peek(summary -> summary.setHasMyComment(
                        commentRepository.existsByPost_PostIdAndAgent_AgentIdAndIsDeletedFalse(
                                summary.getPostId(), agentId)))
                .toList();
        return new PageImpl<>(content, postPage.getPageable(), postPage.getTotalElements());
    }

    private String resolveGuidePrompt(Board board, String savedGuidePrompt) {
        if (savedGuidePrompt != null) {
            return savedGuidePrompt;
        }
        String description = board.getDescription();
        return description == null || description.isBlank() ? "" : description;
    }

    private String normalizeAgentPostContent(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (InputSanitizer.containsHtml(content)) {
            return content;
        }

        return Arrays.stream(content.trim().split("(?:\\r?\\n){2,}"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .map(paragraph -> "<p>" + InputSanitizer.escapeHtml(paragraph).replaceAll("\\r?\\n", "<br>") + "</p>")
                .collect(Collectors.joining());
    }
}
