package com.weedrice.whiteboard.domain.actor.integration;

import com.weedrice.whiteboard.domain.actor.ActorBatchReadPort;
import com.weedrice.whiteboard.domain.actor.ActorReadPort;
import com.weedrice.whiteboard.domain.actor.ActorWritePort;
import com.weedrice.whiteboard.domain.actor.AuthorSnapshot;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActorIntegrationAdapter implements ActorReadPort, ActorBatchReadPort, ActorWritePort {

    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final SanctionService sanctionService;

    @Override
    public AuthorSnapshot resolveAuthor(ContentActorRef actorRef) {
        AuthorSnapshot snapshot = resolveAuthors(Set.of(actorRef)).get(actorRef);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return snapshot;
    }

    @Override
    public Map<ContentActorRef, AuthorSnapshot> resolveAuthors(Collection<ContentActorRef> actorRefs) {
        if (actorRefs == null || actorRefs.isEmpty()) {
            return Map.of();
        }

        Set<ContentActorRef> distinctRefs = Set.copyOf(actorRefs);
        Map<Long, User> users = userRepository.findAllById(distinctRefs.stream()
                        .map(ContentActorRef::ownerUserId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, Agent> agents = agentRepository.findAllById(distinctRefs.stream()
                        .map(ContentActorRef::agentId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Agent::getAgentId, Function.identity()));

        Map<ContentActorRef, AuthorSnapshot> result = new LinkedHashMap<>();
        for (ContentActorRef ref : distinctRefs) {
            User owner = users.get(ref.ownerUserId());
            if (owner == null) {
                continue;
            }
            Agent agent = ref.agentId() == null ? null : agents.get(ref.agentId());
            if (agent != null && Objects.equals(agent.getUserId(), ref.ownerUserId())) {
                result.put(ref, new AuthorSnapshot(
                        owner.getUserId(), agent.getAgentId(), "AGENT", agent.getName(),
                        owner.getProfileImageUrl(), owner.getRepresentativeBadgeCode()));
            } else if (ref.agentId() == null) {
                result.put(ref, new AuthorSnapshot(
                        owner.getUserId(), null, "USER", owner.getDisplayName(),
                        owner.getProfileImageUrl(), owner.getRepresentativeBadgeCode()));
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public ContentActorRef validateForWrite(ContentActorRef actorRef) {
        User owner = userRepository.findById(actorRef.ownerUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!owner.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        sanctionService.validateNotBanned(owner);

        if (actorRef.agentId() != null) {
            Agent agent = agentRepository.findByAgentIdAndIsDeletedFalse(actorRef.agentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
            if (!Objects.equals(agent.getUserId(), owner.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (!agent.isActive()) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        return actorRef;
    }
}
