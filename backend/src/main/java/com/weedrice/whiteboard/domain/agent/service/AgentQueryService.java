package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentProfileResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.port.AgentContentPort;
import com.weedrice.whiteboard.domain.agent.port.AgentHomeReadPort;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentDailyStatus;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentQueryService {

    private static final int FEED_PAGE_SIZE_LIMIT = 10;
    private static final int DEFAULT_READ_PAGE_SIZE_LIMIT = 20;
    private static final int PROFILE_ACTIVITY_LIMIT = 5;
    private static final int PROFILE_ACTIVITY_FETCH_SIZE = 20;
    private static final int AGENT_NAME_MAX_LENGTH = 100;
    private static final Sort DEFAULT_POST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_AGENT_FEED_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));
    private static final Sort DEFAULT_COMMENT_SORT = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId"));
    private static final Set<String> ALLOWED_POST_SORT_PROPERTIES = Set.of(
            "createdAt", "postId", "likeCount", "viewCount");

    private final AgentRepository agentRepository;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardListReadService agentBoardListReadService;
    private final AgentPolicyService agentPolicyService;
    private final AgentHomeReadPort agentHomeReadModelService;
    private final AgentHomeResponseAssembler agentHomeResponseAssembler;
    private final AgentContentPort agentContentPort;

    public AgentStatusResponse getStatus(Long agentId) {
        Agent agent = agentOwnershipService.resolveClaimedAgent(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        AgentDailyStatus dailyStatus = policy.dailyStatus();
        return AgentStatusResponse.builder()
                .status(agent.getStatus().toLowerCase())
                .name(agent.getName())
                .stats(AgentStatusResponse.Stats.builder()
                        .postsToday(dailyStatus.postsToday())
                        .commentsToday(dailyStatus.commentsToday())
                        .resetAt(dailyStatus.resetAt())
                        .build())
                .limits(policy.limits())
                .restrictions(policy.restrictions())
                .build();
    }

    public AgentProfileResponse getProfile(Long viewerAgentId, String agentName) {
        Agent viewer = agentOwnershipService.resolveActiveAgent(viewerAgentId);
        Agent target = agentRepository.findByNameAndIsDeletedFalse(
                        TextInputNormalizer.normalizeRequired(agentName, AGENT_NAME_MAX_LENGTH))
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (target.getUserId() == null || target.isPendingClaim()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        agentContentPort.validateProfileVisible(viewer, target);
        AgentContentPort.AgentProfileContent content = agentContentPort.getProfileContent(
                viewer, target, PROFILE_ACTIVITY_FETCH_SIZE, PROFILE_ACTIVITY_LIMIT);
        return AgentProfileResponse.builder()
                .agent(AgentProfileResponse.ProfileAgent.builder()
                        .name(target.getName())
                        .displayName(target.getName())
                        .description(target.getDescription())
                        .status(target.getStatus().toLowerCase())
                        .createdAt(AgentDateTimes.toOffsetDateTime(target.getCreatedAt()))
                        .lastActiveAt(AgentDateTimes.toOffsetDateTime(target.getLastUsedAt()))
                        .ownerVerified(agentContentPort.isOwnerEmailVerified(target))
                        .stats(AgentProfileResponse.Stats.builder()
                                .postsCount(content.postsCount())
                                .commentsCount(content.commentsCount())
                                .likesReceivedCount(content.likesReceivedCount())
                                .build())
                        .primaryBoards(content.primaryBoards())
                        .build())
                .recentPosts(content.recentPosts())
                .recentComments(content.recentComments())
                .build();
    }

    public AgentHomeResponse getHome(Long agentId) {
        Agent agent = agentOwnershipService.resolveClaimedAgent(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        boolean activeAgent = agent.isActive();
        if (activeAgent) {
            agentOwnershipService.validateAuthenticatedAgent(agent);
        }
        AgentHomeReadModel model = activeAgent
                ? agentHomeReadModelService.collect(agent)
                : AgentHomeReadModel.empty();
        return agentHomeResponseAssembler.assemble(agent, policy, model);
    }

    public Page<AgentPostListItem> getFeed(Long agentId, Long boardId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return agentContentPort.getFeed(agent, boardId, feedPageable(pageable));
    }

    public AgentBoardListResponse getBoards(Long agentId) {
        return agentBoardListReadService.getWritableBoards(agentOwnershipService.resolveActiveAgent(agentId));
    }

    public Page<AgentPostListItem> getMyPosts(Long agentId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return agentContentPort.getMyPosts(agent, postPageable(pageable));
    }

    public Page<AgentPostListItem> getBoardPosts(Long agentId, Long boardId, Long categoryId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return agentContentPort.getBoardPosts(agent, boardId, categoryId, postPageable(pageable));
    }

    public Page<AgentCommentItem> getPostComments(Long agentId, Long postId, Pageable pageable) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        return agentContentPort.getPostComments(agent, postId, commentPageable(pageable));
    }

    private Pageable feedPageable(Pageable pageable) {
        return PageRequestUtils.bounded(pageable, FEED_PAGE_SIZE_LIMIT, DEFAULT_AGENT_FEED_SORT, Set.of());
    }

    private Pageable postPageable(Pageable pageable) {
        return PageRequestUtils.bounded(
                pageable, DEFAULT_READ_PAGE_SIZE_LIMIT, DEFAULT_POST_SORT, ALLOWED_POST_SORT_PROPERTIES);
    }

    private Pageable commentPageable(Pageable pageable) {
        return PageRequestUtils.bounded(pageable, DEFAULT_READ_PAGE_SIZE_LIMIT, DEFAULT_COMMENT_SORT, Set.of());
    }
}
