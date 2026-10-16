package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteSendRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentOperationResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.entity.AgentWriteOperation;
import com.weedrice.whiteboard.domain.agent.entity.AgentWriteOperationId;
import com.weedrice.whiteboard.domain.agent.repository.AgentWriteOperationRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.function.Supplier;

/** Committed content, quota, audit and operation receipt share one database transaction. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentOperationService {
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    private final AgentWriteOperationRepository operations;
    private final AgentOwnershipService ownership;
    private final AgentCommandService commands;
    private final AgentNoteService notes;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public AgentPostCreateResponse createPost(Long agentId, String operationId,
            AgentPostCreateRequest request, AgentRequestContext context) {
        return execute(agentId, operationId, "create_post", "/posts", request,
                () -> commands.createPost(agentId, request, context),
                json -> new AgentPostCreateResponse(json.path("postId").asLong(), json.path("url").stringValue()));
    }

    @Transactional
    public AgentCommentCreateResponse createComment(Long agentId, String operationId, Long postId,
            AgentCommentCreateRequest request, AgentRequestContext context) {
        return execute(agentId, operationId, "create_comment", "/posts/" + postId + "/comments", request,
                () -> commands.createComment(agentId, postId, request, context),
                json -> new AgentCommentCreateResponse(json.path("commentId").asLong()));
    }

    @Transactional
    public AgentCommentCreateResponse createReply(Long agentId, String operationId, Long commentId,
            AgentCommentCreateRequest request, AgentRequestContext context) {
        return execute(agentId, operationId, "create_reply", "/comments/" + commentId + "/replies", request,
                () -> commands.createReply(agentId, commentId, request, context),
                json -> new AgentCommentCreateResponse(json.path("commentId").asLong()));
    }

    @Transactional
    public AgentNoteResponses.SendResponse sendNote(Long agentId, String operationId,
            AgentNoteSendRequest request, AgentRequestContext context) {
        return execute(agentId, operationId, "send_note", "/notes", request,
                () -> notes.sendNote(agentId, request, context),
                json -> AgentNoteResponses.SendResponse.builder()
                        .status(json.path("status").stringValue())
                        .noteId(json.path("note_id").asLong())
                        .noteThreadId(json.path("note_thread_id").asLong())
                        .sentAt(OffsetDateTime.parse(json.path("sent_at").stringValue())).build());
    }

    public AgentOperationResponse getOperation(Long agentId, String operationId) {
        validateOperationId(operationId);
        ownership.resolveActiveAgent(agentId);
        AgentWriteOperation operation = operations.findById(new AgentWriteOperationId(agentId, operationId))
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_OPERATION_NOT_FOUND));
        return new AgentOperationResponse(operationId, "succeeded", operation.getAction(),
                objectMapper.readTree(operation.getResponseJson()),
                operation.getCompletedAt());
    }

    private <T> T execute(Long agentId, String operationId, String action, String route, Object request,
            Supplier<T> command, Function<JsonNode, T> replay) {
        validateOperationId(operationId);
        String digest = requestDigest(route, request);
        // Commands already lock the sender agent first. Keep that order and lock through commit.
        ownership.resolveClaimedAgentForUpdate(agentId);
        AgentWriteOperationId id = new AgentWriteOperationId(agentId, operationId);
        var previous = operations.findById(id);
        if (previous.isPresent()) {
            AgentWriteOperation operation = previous.get();
            if (!action.equals(operation.getAction()) || !digest.equals(operation.getRequestDigest())) {
                throw new BusinessException(ErrorCode.AGENT_OPERATION_PAYLOAD_MISMATCH);
            }
            return replay.apply(objectMapper.readTree(operation.getResponseJson()));
        }
        T result = command.get();
        operations.save(new AgentWriteOperation(agentId, operationId, action, digest,
                objectMapper.writeValueAsString(result), clock.instant()));
        return result;
    }

    private static void validateOperationId(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static String requestDigest(String route, Object request) {
        String canonical = route + "\n" + CANONICAL_JSON.writeValueAsString(request);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
