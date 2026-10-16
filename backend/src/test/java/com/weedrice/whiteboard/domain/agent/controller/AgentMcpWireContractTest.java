package com.weedrice.whiteboard.domain.agent.controller;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentOperationResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.service.AgentCommandService;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.agent.service.AgentNoteService;
import com.weedrice.whiteboard.domain.agent.service.AgentOperationService;
import com.weedrice.whiteboard.domain.agent.service.AgentPostActivityService;
import com.weedrice.whiteboard.domain.agent.service.AgentPostImageService;
import com.weedrice.whiteboard.domain.agent.service.AgentQueryService;
import com.weedrice.whiteboard.domain.agent.service.AgentRulesService;
import com.weedrice.whiteboard.domain.agent.web.AgentRequestContextResolver;
import com.weedrice.whiteboard.global.config.LocalDateTimeWireModule;
import com.weedrice.whiteboard.global.security.AgentPrincipal;
import com.weedrice.whiteboard.global.security.CurrentAgentIdArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real controller binding and DTO wire serialization; fixtures contain synthetic data only. */
class AgentMcpWireContractTest {
    private static final String KEY = "12345678-1234-4321-8123-123456789abc";
    private final JsonMapper mapper = JsonMapper.builder().addModule(new LocalDateTimeWireModule())
            .enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    private final AgentQueryService queries = mock(AgentQueryService.class);
    private final AgentOperationService operations = mock(AgentOperationService.class);

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    private MockMvc mvc() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AgentPrincipal(7L, 70L, "contract-agent", "ACTIVE"), null, List.of()));
        AgentController controller = new AgentController(operations, mock(AgentLifecycleService.class), queries,
                mock(AgentCommandService.class), mock(AgentNoteService.class), mock(AgentPostActivityService.class),
                mock(AgentPostImageService.class), mock(AgentRulesService.class), mock(AgentRequestContextResolver.class));
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentAgentIdArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper)).build();
    }

    @Test
    void generatedFixturesMatchRealControllerBindingAndDtoSerialization() throws Exception {
        MockMvc mvc = mvc();
        var post = AgentPostListItem.builder().postId(101L).boardId(11L).boardName("에이전트 광장")
                .boardUrl("agents").categoryId(12L).title("함께 배우는 한국어")
                .content("<p>오늘 배운 표현을 나눕니다.</p>\n<p>맥락과 본문을 끝까지 읽어 주세요.</p>")
                .commentCount(4).likeCount(3).isNotice(false).isSecret(false).hasMyComment(true)
                .createdAt(LocalDateTime.of(2026, 10, 16, 9, 30))
                .author(AgentPostListItem.Author.builder().userId(70L).agentId(7L)
                        .authorType("AGENT").displayName("계약 테스트 에이전트").build()).build();
        when(queries.getFeed(eq(7L), eq(11L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(1, 1), 3));
        List<AgentCommentItem> comments = List.of(comment(201L, null, "ACTIVE"), comment(202L, null, "DELETED"),
                comment(203L, null, "BLINDED"), comment(204L, null, "BLOCKED_AUTHOR"));
        when(queries.getPostComments(eq(7L), eq(101L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(comments, PageRequest.of(0, 4), 5));
        when(queries.getCommentReplies(eq(7L), eq(202L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment(205L, 202L, "ACTIVE")), PageRequest.of(1, 1), 3));
        when(operations.getOperation(7L, KEY)).thenReturn(new AgentOperationResponse(KEY, "succeeded", "create_post",
                mapper.valueToTree(new AgentPostCreateResponse(101L, "https://noviis.kr/agents/101")),
                Instant.parse("2026-10-16T00:30:00Z")));
        Map<String, Object> fixtures = new LinkedHashMap<>();
        fixtures.put("feed", exchange(mvc, "/api/v1/agents/feed", Map.of("boardId", "11", "page", "1", "size", "1")));
        fixtures.put("comments", exchange(mvc, "/api/v1/agents/posts/101/comments", Map.of("page", "0", "size", "4")));
        fixtures.put("replies", exchange(mvc, "/api/v1/agents/comments/202/replies", Map.of("page", "1", "size", "1")));
        fixtures.put("operation", exchange(mvc, "/api/v1/agents/operations/" + KEY, Map.of()));
        verify(queries).getFeed(7L, 11L, PageRequest.of(1, 1));
        verify(queries).getPostComments(7L, 101L, PageRequest.of(0, 4));
        verify(queries).getCommentReplies(7L, 202L, PageRequest.of(1, 1));
        verify(operations).getOperation(7L, KEY);
        JsonNode actual = mapper.valueToTree(fixtures);
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n";
        Path generated = Path.of("build/contracts/agent-mcp-contract.json");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, json, StandardCharsets.UTF_8);
        Path snapshot = Path.of("src/test/resources/contracts/agent-mcp-contract.json");
        if ("true".equals(System.getenv("UPDATE_AGENT_MCP_FIXTURES"))) {
            Files.writeString(snapshot, json, StandardCharsets.UTF_8);
        }
        assertThat(mapper.readTree(Files.readString(snapshot, StandardCharsets.UTF_8))).isEqualTo(actual);
    }

    @Test
    void unsupportedFeedParameterNamesDoNotMasqueradeAsSupportedFilters() throws Exception {
        when(queries.getFeed(eq(7L), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        mvc().perform(get("/api/v1/agents/feed").param("board_id", "11").param("limit", "1").param("cursor", "opaque"))
                .andExpect(status().isOk());
        verify(queries).getFeed(7L, null, PageRequest.of(0, 20));
    }

    private Map<String, Object> exchange(MockMvc mvc, String path, Map<String, String> query) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        query.forEach(request::param);
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return Map.of("request", Map.of("method", "GET", "path", path, "query", new java.util.TreeMap<>(query)),
                "response", mapper.readTree(body));
    }

    private AgentCommentItem comment(long id, Long parent, String status) {
        return AgentCommentItem.builder().commentId(id).parentId(parent).postId(101L)
                .content("ACTIVE".equals(status) ? "본문을 읽고 남기는 답변입니다." : null)
                .depth(parent == null ? 0 : 1).likeCount(2).replyCount(1).hasReplies(true)
                .createdAt(LocalDateTime.of(2026, 10, 16, 9, 45)).status(status)
                .author(("ACTIVE".equals(status) || "BLINDED".equals(status)) ? AgentCommentItem.Author.builder().userId(70L).agentId(7L)
                        .authorType("AGENT").displayName("계약 테스트 에이전트").build() : null).build();
    }
}
