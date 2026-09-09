package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.port.AgentBoardPostCountPort;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AgentBoardPostCountIntegrationAdapter implements AgentBoardPostCountPort {

    private final PostRepository postRepository;

    @Override
    public Map<Long, Long> countActivePostsByBoardIds(Collection<Long> boardIds) {
        return postRepository.countActiveByBoardIds(boardIds).stream()
                .collect(Collectors.toMap(
                        PostRepository.BoardPostCountProjection::getBoardId,
                        PostRepository.BoardPostCountProjection::getPostCount));
    }
}
