package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.config.AgentHomeProperties;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentDailyStatus;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.global.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic data serialized through production DTOs for the companion MCP tests. */
class AgentHomeBoardWireContractTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void homeAndBoardsMatchCommittedWireContract() throws Exception {
        Map<String, Object> exchanges = new LinkedHashMap<>();
        AgentBoardItem board = AgentBoardItem.builder().boardId(9L).boardName("Language learning")
                .boardUrl("language_study").description("Synthetic board")
                .guidePrompt("Discuss language learning.").postCount(100).categories(List.of()).build();
        exchanges.put("boards", exchange("/api/v1/agents/boards", new AgentBoardListResponse(List.of(board))));
        exchanges.put("home", exchange("/api/v1/agents/home", home()));
        JsonNode actual = mapper.readTree(mapper.writeValueAsString(exchanges));

        assertThat(actual.at("/boards/response/data/boards/0/postCount").asLong()).isEqualTo(100);
        assertThat(actual.at("/home/response/data/agent/is_new_agent").asBoolean()).isTrue();
        assertThat(actual.at("/home/response/data/soft_guidance/0/text").isString()).isTrue();
        assertThat(actual.at("/home/response/data/style_guidance/0/code").isString()).isTrue();
        assertThat(actual.at("/home/response/data/opportunities/0/available_actions/1/params/post_id").isIntegralNumber()).isTrue();

        Path snapshot = Path.of("src/test/resources/contracts/agent-home-board-contract.json");
        if ("true".equals(System.getenv("UPDATE_AGENT_HOME_BOARD_CONTRACT"))) {
            Files.createDirectories(snapshot.getParent());
            Files.writeString(snapshot, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n",
                    StandardCharsets.UTF_8);
        }
        assertThat(Files.exists(snapshot)).as("Generate the reviewed fixture with UPDATE_AGENT_HOME_BOARD_CONTRACT=true").isTrue();
        assertThat(mapper.readTree(Files.readString(snapshot, StandardCharsets.UTF_8))).isEqualTo(actual);
    }

    private Map<String, Object> exchange(String path, Object response) {
        return Map.of("request", Map.of("method", "GET", "path", path, "query", Map.of()),
                "response", ApiResponse.success(response));
    }

    private AgentHomeResponse home() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        AgentHomeResponseAssembler assembler = new AgentHomeResponseAssembler(new AgentHomeProperties(), clock);
        Agent agent = Agent.builder().name("contract-agent").status(Agent.STATUS_ACTIVE).build();
        ReflectionTestUtils.setField(agent, "createdAt", LocalDateTime.of(2026, 11, 1, 12, 0));
        AgentPolicySnapshot policy = new AgentPolicySnapshot(
                new AgentDailyStatus(LocalDate.of(2026, 11, 5), 3, 5, OffsetDateTime.parse("2026-11-06T00:00:00+09:00")),
                AgentLimits.builder().maxPostsPerDay(50).maxCommentsPerDay(100).maxNotesPerDay(20)
                        .postsRemaining(47).commentsRemaining(95).notesRemaining(20).build(),
                AgentRestrictions.builder().canPost(true).canComment(true).canSendNote(true).build(), false);
        AgentHomeReadModel reads = new AgentHomeReadModel(true,
                AgentNoteResponses.Summary.builder().unreadThreadCount(0).unreadNoteCount(0).build(),
                List.of(),
                List.of(AgentHomeResponse.MyRecentPost.builder().postId(170L).title("Own thread")
                        .boardId(9L).boardName("Language learning").build()),
                List.of(AgentHomeResponse.RecommendedBoard.builder().boardId(9L).boardUrl("language_study")
                        .name("Language learning").postCount(100).build()),
                List.of(AgentHomeResponse.RecentFeedItem.builder().postId(171L).title("Recent thread")
                        .boardId(9L).boardName("Language learning").contentPreview("Synthetic preview").build()));
        return assembler.assemble(agent, policy, reads);
    }
}
