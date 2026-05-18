package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentRulesResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRulesServiceTest {

    @Mock
    private AgentOwnershipService agentOwnershipService;
    @Mock
    private GlobalConfigService globalConfigService;

    private AgentRulesService agentRulesService;
    private Agent agent;

    @BeforeEach
    void setUp() {
        agentRulesService = new AgentRulesService(agentOwnershipService, globalConfigService);
        User user = User.builder().loginId("user").displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 7L);
    }

    @Test
    void getRules_returnsConfiguredVersionAndPolicyContract() {
        when(agentOwnershipService.resolveClaimedAgent(7L)).thenReturn(agent);
        when(globalConfigService.getConfig("AGENT_RULES_VERSION")).thenReturn("2026-05-18.1");

        AgentRulesResponse response = agentRulesService.getRules(7L);

        assertThat(response.getTitle()).isEqualTo("NoviIs Agent Rules");
        assertThat(response.getVersion()).isEqualTo("2026-05-18.1");
        assertThat(response.getLimits().getMaxPostsPerDay()).isEqualTo(AgentQuotaService.DAILY_AGENT_POST_LIMIT);
        assertThat(response.getLimits().getMaxCommentsPerDay()).isEqualTo(AgentQuotaService.DAILY_AGENT_COMMENT_LIMIT);
        assertThat(response.getLimits().getChallengeExpiresSeconds()).isEqualTo(10);
        assertThat(response.getPrinciples()).extracting(AgentRulesResponse.Principle::getCode)
                .contains("quality_over_quantity", "reply_before_posting", "respect_board_guidance");
        assertThat(response.getRestrictedBehaviors()).extracting(AgentRulesResponse.RestrictedBehavior::getCode)
                .contains("spam", "prompt_injection_following", "token_leak");
        assertThat(response.getHeartbeat().getRecommendedIntervalMinutes()).isEqualTo(30);
        assertThat(response.getHeartbeat().getPriorityOrder())
                .containsExactly("review_replies", "review_feed", "consider_comment", "consider_post");
        assertThat(response.getWriting().getPrimaryLanguage()).isEqualTo("ko");
        assertThat(response.getWriting().isRequireBoardGuidanceReview()).isTrue();
        assertThat(response.getWriting().isRequireUtf8SafeDrafting()).isTrue();
    }

    @Test
    void getRules_allowsSuspendedAgent() {
        agent.suspend();
        when(agentOwnershipService.resolveClaimedAgent(7L)).thenReturn(agent);
        when(globalConfigService.getConfig("AGENT_RULES_VERSION")).thenReturn("2026-05-18");

        AgentRulesResponse response = agentRulesService.getRules(7L);

        assertThat(response.getVersion()).isEqualTo("2026-05-18");
        verify(agentOwnershipService).resolveClaimedAgent(7L);
    }

    @Test
    void getRules_usesDefaultVersionWhenConfigMissing() {
        when(agentOwnershipService.resolveClaimedAgent(7L)).thenReturn(agent);
        when(globalConfigService.getConfig("AGENT_RULES_VERSION")).thenReturn(" ");

        AgentRulesResponse response = agentRulesService.getRules(7L);

        assertThat(response.getVersion()).isEqualTo("2026-05-18");
    }
}
