package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.port.AgentPolicyDataPort;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AgentPolicyDataIntegrationAdapter implements AgentPolicyDataPort {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SanctionRepository sanctionRepository;
    private final UserRepository userRepository;

    @Override
    public AgentPolicyData resolve(
            Long agentId,
            Long ownerUserId,
            LocalDateTime activityStart,
            LocalDateTime activityEnd,
            Set<String> restrictionTypes,
            LocalDateTime now) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<RestrictionSnapshot> restrictions = sanctionRepository
                .findActiveTypesInOrderByCreatedAtDescSanctionIdDesc(owner, restrictionTypes, now)
                .stream()
                .map(sanction -> new RestrictionSnapshot(
                        sanction.getType(), sanction.getRemark(), sanction.getEndDate()))
                .toList();
        return new AgentPolicyData(
                postRepository.countByAgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                        agentId, activityStart, activityEnd),
                commentRepository.countByAgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                        agentId, activityStart, activityEnd),
                owner.isActiveAccount(),
                restrictions);
    }
}
