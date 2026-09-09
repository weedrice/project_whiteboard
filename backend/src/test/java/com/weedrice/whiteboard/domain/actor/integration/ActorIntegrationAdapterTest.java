package com.weedrice.whiteboard.domain.actor.integration;

import com.weedrice.whiteboard.domain.actor.AuthorSnapshot;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActorIntegrationAdapterTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private SanctionService sanctionService;

    @Test
    void resolveAuthorsLoadsUsersAndAgentsOncePerPage() {
        User human = user(1L, "Human");
        User owner = user(2L, "Owner");
        Agent agent = Agent.builder().user(owner).name("Agent").status(Agent.STATUS_ACTIVE).build();
        ReflectionTestUtils.setField(agent, "agentId", 20L);

        ContentActorRef humanRef = new ContentActorRef(1L, null);
        ContentActorRef agentRef = new ContentActorRef(2L, 20L);
        when(userRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(human, owner));
        when(agentRepository.findAllById(Set.of(20L))).thenReturn(List.of(agent));

        Map<ContentActorRef, AuthorSnapshot> authors = adapter().resolveAuthors(
                List.of(humanRef, agentRef, agentRef));

        assertThat(authors.get(humanRef).displayName()).isEqualTo("Human");
        assertThat(authors.get(agentRef).displayName()).isEqualTo("Agent");
        verify(userRepository, times(1)).findAllById(Set.of(1L, 2L));
        verify(agentRepository, times(1)).findAllById(Set.of(20L));
    }

    @Test
    void resolveAuthorsSkipsRepositoriesForEmptyPage() {
        assertThat(adapter().resolveAuthors(List.of())).isEmpty();
        verifyNoInteractions(userRepository, agentRepository);
    }

    private ActorIntegrationAdapter adapter() {
        return new ActorIntegrationAdapter(userRepository, agentRepository, sanctionService);
    }

    private User user(Long userId, String displayName) {
        User user = User.builder().displayName(displayName).build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
