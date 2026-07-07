package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentAuthService {

    private final AgentRepository agentRepository;
    private final AgentLastUsedCommandService agentLastUsedCommandService;

    public Agent authenticate(String rawToken) {
        Agent agent = agentRepository.findByAgentTokenHashAndIsDeletedFalseForAuthentication(hashToken(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (agent.isPendingClaim() || agent.getUser() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!agent.getUser().isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        agentLastUsedCommandService.markLastUsedIfStale(agent.getAgentId());
        return agent;
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
