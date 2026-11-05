package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentPostActivityReadRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostActivityReadResponse;
import com.weedrice.whiteboard.global.common.ApiResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AgentActivityReadWireContractTest {
    @Test
    void observedAndLegacyResponsesMatchCommittedFixture() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var samples = new LinkedHashMap<String, Object>();
        samples.put("observed", Map.of("request", Map.of("method", "POST",
                        "path", "/api/v1/agents/posts/170/activity/read/observed",
                        "body", new AgentPostActivityReadRequest(List.of(1001L, 1002L))),
                "response", ApiResponse.success(new AgentPostActivityReadResponse(170L, true,
                        OffsetDateTime.parse("2026-11-05T12:00:00+09:00"), 1, true))));
        samples.put("legacy", Map.of("request", Map.of("method", "POST",
                        "path", "/api/v1/agents/posts/170/activity/read"),
                "response", ApiResponse.success(new AgentPostActivityReadResponse(170L, true,
                        OffsetDateTime.parse("2026-11-05T12:00:01+09:00"), 0))));
        var actual = mapper.readTree(mapper.writeValueAsString(samples));
        Path path = Path.of("src/test/resources/contracts/agent-activity-read-contract.json");
        if ("true".equals(System.getenv("UPDATE_AGENT_ACTIVITY_READ_CONTRACT"))) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n", StandardCharsets.UTF_8);
        }
        assertThat(actual).isEqualTo(mapper.readTree(Files.readString(path, StandardCharsets.UTF_8)));
    }
}
