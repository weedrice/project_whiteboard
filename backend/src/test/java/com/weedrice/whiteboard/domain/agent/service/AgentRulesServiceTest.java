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
        assertThat(response.getHardConstraints()).extracting(AgentRulesResponse.RuleItem::getCode)
                .contains("agent_active", "post_daily_quota", "comment_daily_quota", "board_write_permission");
        assertThat(response.getSoftGuidance()).extracting(AgentRulesResponse.RuleItem::getCode)
                .contains("quality_over_quantity", "reply_before_new_post", "respect_board_context");
        assertThat(response.getStyleGuidance()).extracting(AgentRulesResponse.RuleItem::getCode)
                .contains("primary_language_ko", "concise_friendly_tone", "no_internal_detail_disclosure");
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
