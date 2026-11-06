package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.port.AgentBoardPostCountPort;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashMap;

@Component
@RequiredArgsConstructor
public class AgentBoardPostCountIntegrationAdapter implements AgentBoardPostCountPort {

    private final PostRepository postRepository;

    @Override
    public Map<Long, Long> countVisiblePostsByBoardIds(Collection<Long> boardIds, Collection<Long> adminBoardIds) {
        if (boardIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> managedBoardIds = Set.copyOf(adminBoardIds);
        List<Long> publicBoardIds = boardIds.stream().distinct()
                .filter(boardId -> !managedBoardIds.contains(boardId)).toList();
        List<Long> activeCountBoardIds = boardIds.stream().distinct()
                .filter(managedBoardIds::contains).toList();
        Map<Long, Long> counts = new HashMap<>();
        if (!publicBoardIds.isEmpty()) {
            postRepository.countPublicVisiblePostsByBoardIds(publicBoardIds)
                    .forEach(count -> counts.put(count.getBoardId(), count.getPostCount()));
        }
        if (!activeCountBoardIds.isEmpty()) {
            postRepository.countActiveByBoardIds(activeCountBoardIds)
                    .forEach(count -> counts.put(count.getBoardId(), count.getPostCount()));
        }
        return counts;
    }
}
