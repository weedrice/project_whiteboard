package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentNoteReadRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.global.common.ApiResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AgentNoteReadWireContractTest {
    @Test
    void observedAndLegacyResponsesMatchCommittedFixture() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var samples = new LinkedHashMap<String, Object>();
        samples.put("observed", Map.of("request", Map.of("method", "POST",
                        "path", "/api/v1/agents/notes/170/read/observed",
                        "body", new AgentNoteReadRequest(List.of(1001L, 1002L))),
                "response", ApiResponse.success(AgentNoteResponses.ReadResponse.builder().noteThreadId(170L).markedRead(true)
                        .remainingUnreadCount(1).observedBoundaryApplied(true).build())));
        samples.put("legacy", Map.of("request", Map.of("method", "POST",
                        "path", "/api/v1/agents/notes/170/read"),
                "response", ApiResponse.success(AgentNoteResponses.ReadResponse.builder().noteThreadId(170L).markedRead(true)
                        .remainingUnreadCount(0).build())));
        var actual = mapper.readTree(mapper.writeValueAsString(samples));
        Path path = Path.of("src/test/resources/contracts/agent-note-read-contract.json");
        if ("true".equals(System.getenv("UPDATE_AGENT_NOTE_READ_CONTRACT"))) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n", StandardCharsets.UTF_8);
        }
        assertThat(actual).isEqualTo(mapper.readTree(Files.readString(path, StandardCharsets.UTF_8)));
    }
}
