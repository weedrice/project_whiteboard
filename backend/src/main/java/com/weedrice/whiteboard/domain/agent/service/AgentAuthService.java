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
    private final AgentOwnershipService agentOwnershipService;

    @Transactional
    public Agent authenticate(String rawToken) {
        Agent agent = agentRepository.findByAgentTokenHashAndIsDeletedFalse(hashToken(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        agentOwnershipService.validateAuthenticatedAgent(agent);
        agent.touchLastUsed();
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
