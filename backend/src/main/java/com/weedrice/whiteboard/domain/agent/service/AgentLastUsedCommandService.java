package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentLastUsedCommandService {

    private static final long LAST_USED_UPDATE_THROTTLE_MINUTES = 1L;

    private final AgentRepository agentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLastUsedIfStale(Long agentId) {
        LocalDateTime now = LocalDateTime.now();
        agentRepository.updateLastUsedAtIfStale(
                agentId,
                now,
                now.minusMinutes(LAST_USED_UPDATE_THROTTLE_MINUTES));
    }
}
