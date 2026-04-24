package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentClaimRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentLifecycleService {

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
    private final UserRepository userRepository;
    private final AgentAuditService agentAuditService;
    private final SanctionPolicyService sanctionPolicyService;

    @Value("${app.agent.pending-claim-ttl-hours:24}")
    private long pendingClaimTtlHours = 24L;

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

    @Transactional(noRollbackFor = ExpiredPendingClaimNotFoundException.class)
    public AgentResponse claim(Long userId, AgentClaimRequest request, HttpServletRequest httpServletRequest) {
        User user = resolveActiveOwnerForUpdate(userId);
        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        Agent agent = agentRepository.findByAgentTokenHashAndIsDeletedFalseForUpdate(hashToken(request.getAgentToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

        if (agent.isPendingClaim() && isPendingClaimExpired(agent)) {
            agent.softDelete();
            throw new ExpiredPendingClaimNotFoundException();
        }

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
                agentAuditService.saveLog(agent, user, "REACTIVATE", "AGENT", agent.getAgentId(), httpServletRequest);
            }
            return AgentResponse.from(agent);
        }

        softDeleteOtherAgentsForUser(user, agent.getAgentId(), httpServletRequest);
        agent.claim(user);
        agentAuditService.saveLog(agent, user, "CLAIM", "AGENT", agent.getAgentId(), httpServletRequest);
        return AgentResponse.from(agent);
    }

    private static class ExpiredPendingClaimNotFoundException extends BusinessException {
        private ExpiredPendingClaimNotFoundException() {
            super(ErrorCode.AGENT_NOT_FOUND);
        }
    }

    @Transactional
    public int softDeleteExpiredPendingClaims() {
        List<Agent> expiredAgents = agentRepository.findExpiredPendingClaimsForUpdate(
                Agent.STATUS_PENDING_CLAIM,
                resolvePendingClaimExpiresBefore());
        expiredAgents.forEach(Agent::softDelete);
        return expiredAgents.size();
    }

    public AgentListResponse getMyAgents(Long userId) {
        User user = resolveActiveOwner(userId);
        List<AgentResponse> agents = agentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                .map(AgentResponse::from)
                .collect(Collectors.toList());
        return new AgentListResponse(agents);
    }

    @Transactional
    public AgentResponse suspendMyAgent(Long userId, Long agentId, HttpServletRequest request) {
        User user = resolveActiveOwner(userId);
        Agent agent = getOwnedAgent(user, agentId);
        agent.suspend();
        agentAuditService.saveLog(agent, agent.getUser(), "SUSPEND", "AGENT", agent.getAgentId(), request);
        return AgentResponse.from(agent);
    }

    @Transactional
    public AgentResponse activateMyAgent(Long userId, Long agentId, HttpServletRequest request) {
        User user = resolveActiveOwnerForUpdate(userId);
        Agent agent = getOwnedAgentForUpdate(user, agentId);
        if (agent.isActive()) {
            return AgentResponse.from(agent);
        }
        if (!agent.isSuspended()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (agent.getName() == null || agent.getName().isBlank()) {
            agent.restoreDisplayInfo(resolveAgentName(null), "");
        }
        agent.activate();
        agentAuditService.saveLog(agent, user, "REACTIVATE", "AGENT", agent.getAgentId(), request);
        return AgentResponse.from(agent);
    }

    @Transactional
    public void deleteMyAgent(Long userId, Long agentId, HttpServletRequest request) {
        User user = resolveActiveOwner(userId);
        Agent agent = getOwnedAgent(user, agentId);
        agent.softDelete();
        agentAuditService.saveLog(agent, agent.getUser(), "DELETE", "AGENT", agent.getAgentId(), request);
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
                    agentAuditService.saveLog(existingAgent, user, "DELETE", "AGENT", existingAgent.getAgentId(), request);
                });
    }

    private boolean isPendingClaimExpired(Agent agent) {
        LocalDateTime createdAt = agent.getCreatedAt();
        return createdAt != null
                && agent.getUser() == null
                && agent.getClaimedAt() == null
                && createdAt.isBefore(resolvePendingClaimExpiresBefore());
    }

    private LocalDateTime resolvePendingClaimExpiresBefore() {
        return LocalDateTime.now().minusHours(Math.max(1L, pendingClaimTtlHours));
    }

    private User resolveActiveOwnerForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateActiveOwner(user);
        return user;
    }

    private User resolveActiveOwner(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateActiveOwner(user);
        return user;
    }

    private void validateActiveOwner(User user) {
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        sanctionPolicyService.validateNotBanned(user);
    }

    private Agent getOwnedAgent(User user, Long agentId) {
        Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (agent.getUser() == null || !Objects.equals(agent.getUser().getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return agent;
    }

    private Agent getOwnedAgentForUpdate(User user, Long agentId) {
        Agent agent = agentRepository.findByAgentIdForUpdate(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (agent.getUser() == null || !Objects.equals(agent.getUser().getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return agent;
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

}
