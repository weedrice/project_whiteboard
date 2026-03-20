package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.*;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentActivityLog;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AgentRepository agentRepository;
    private final AgentActivityLogRepository agentActivityLogRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
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
                .name(request.getName())
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
        Agent agent = agentRepository.findByAgentTokenHashAndIsDeletedFalse(hashToken(request.getAgentToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

        if (!agent.isPendingClaim()) {
            if (agent.getUser() != null && !Objects.equals(agent.getUser().getUserId(), userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Agent token is already claimed by another user");
            }
            if (!Objects.equals(agent.getUser() != null ? agent.getUser().getUserId() : null, userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return AgentResponse.from(agent);
        }

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

    public AgentFeedResponse getFeed(Long agentId, Long boardId, Pageable pageable) {
        Pageable effectivePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(Math.max(pageable.getPageSize(), 1), 10),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Post> posts = boardId != null
                ? postRepository.findByBoard_BoardId(boardId, effectivePageable)
                : postRepository.findAll(effectivePageable);

        List<AgentFeedItem> items = posts.getContent().stream()
                .filter(post -> !post.getIsDeleted())
                .filter(post -> post.getBoard().getIsActive() && post.getBoard().getIsPublic())
                .map(post -> AgentFeedItem.builder()
                        .postId(post.getPostId())
                        .title(post.getTitle())
                        .contentPreview(toPreview(post.getContents()))
                        .boardId(post.getBoard().getBoardId())
                        .boardUrl(post.getBoard().getBoardUrl())
                        .commentCount(post.getCommentCount())
                        .createdAt(post.getCreatedAt())
                        .hasMyComment(commentRepository.existsByPost_PostIdAndAgent_AgentIdAndIsDeletedFalse(
                                post.getPostId(), agentId))
                        .build())
                .collect(Collectors.toList());

        return new AgentFeedResponse(items);
    }

    @Transactional
    public AgentPostCreateResponse createPost(Long agentId, AgentPostCreateRequest request,
            HttpServletRequest httpServletRequest) {
        Agent agent = getActiveAgent(agentId);
        PostCreateRequest postCreateRequest = new PostCreateRequest(
                null,
                request.getTitle(),
                request.getContent(),
                null,
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
        Comment comment = commentService.createCommentAsAgent(agent.getUser().getUserId(), agentId, postId, null,
                request.getContent());
        saveLog(agent, agent.getUser(), "CREATE_COMMENT", "COMMENT", comment.getCommentId(), httpServletRequest);
        return new AgentCommentCreateResponse(comment.getCommentId());
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

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private String toPreview(String content) {
        if (content == null) {
            return null;
        }
        String stripped = content.replaceAll("<[^>]*>", "").trim();
        return stripped.length() <= 200 ? stripped : stripped.substring(0, 200);
    }
}
