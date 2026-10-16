package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.*;
import com.weedrice.whiteboard.domain.agent.entity.*;
import com.weedrice.whiteboard.domain.agent.repository.AgentWriteOperationRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentOperationServiceTest {
    private static final String KEY = "12345678-1234-4321-8123-123456789abc";
    private final AgentWriteOperationRepository repository = mock(AgentWriteOperationRepository.class);
    private final AgentOwnershipService ownership = mock(AgentOwnershipService.class);
    private final AgentCommandService commands = mock(AgentCommandService.class);
    private final AgentNoteService notes = mock(AgentNoteService.class);
    private final Map<AgentWriteOperationId, AgentWriteOperation> stored = new HashMap<>();
    private final AgentRequestContext context = AgentRequestContext.empty();
    private final Instant now = Instant.parse("2026-10-16T00:00:00Z");
    private AgentOperationService service;

    @BeforeEach
    void setUp() {
        service = new AgentOperationService(repository, ownership, commands, notes,
                JsonMapper.builder().build(), Clock.fixed(now, ZoneOffset.UTC));
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(repository.save(any())).thenAnswer(invocation -> {
            AgentWriteOperation operation = invocation.getArgument(0);
            stored.put(operation.getId(), operation);
            return operation;
        });
    }

    @Test
    void postReplayReturnsOriginalResultWithoutAnotherCommandAndStoresOnlyDigest() {
        AgentPostCreateRequest request = post("original title");
        when(commands.createPost(1L, request, context)).thenReturn(new AgentPostCreateResponse(42L, "/posts/42"));
        service.createPost(1L, KEY, request, context);
        AgentPostCreateResponse replay = service.createPost(1L, KEY, post("original title"), context);
        assertThat(replay.getPostId()).isEqualTo(42);
        assertThat(replay.getUrl()).isEqualTo("/posts/42");
        verify(commands, times(1)).createPost(anyLong(), any(), any());
        var operation = stored.values().iterator().next();
        assertThat(operation.getRequestDigest()).hasSize(64).matches("[0-9a-f]+");
        assertThat(operation.getResponseJson()).doesNotContain("original title", "private content");
        assertThat(operation.getCompletedAt()).isEqualTo(now);
        var order = inOrder(ownership, repository, commands);
        order.verify(ownership).resolveClaimedAgentForUpdate(1L);
        order.verify(repository).findById(any());
        order.verify(commands).createPost(1L, request, context);
        order.verify(repository).save(any());
    }

    @Test
    void changedPayloadConflictsWithoutWriting() {
        when(commands.createPost(anyLong(), any(), any())).thenReturn(new AgentPostCreateResponse(42L, "/posts/42"));
        service.createPost(1L, KEY, post("one"), context);
        assertCode(() -> service.createPost(1L, KEY, post("two"), context), ErrorCode.AGENT_OPERATION_PAYLOAD_MISMATCH);
        verify(commands, times(1)).createPost(anyLong(), any(), any());
    }

    @Test
    void changedTargetOrActionConflicts() {
        AgentCommentCreateRequest request = comment();
        when(commands.createComment(1L, 2L, request, context)).thenReturn(new AgentCommentCreateResponse(9L));
        service.createComment(1L, KEY, 2L, request, context);
        assertCode(() -> service.createComment(1L, KEY, 3L, request, context), ErrorCode.AGENT_OPERATION_PAYLOAD_MISMATCH);
        assertCode(() -> service.createReply(1L, KEY, 2L, request, context), ErrorCode.AGENT_OPERATION_PAYLOAD_MISMATCH);
        verify(commands, never()).createReply(any(), any(), any(), any());
    }

    @Test
    void commentAndReplyReplaysPreserveIds() {
        AgentCommentCreateRequest request = comment();
        when(commands.createComment(1L, 2L, request, context)).thenReturn(new AgentCommentCreateResponse(9L));
        when(commands.createReply(2L, 9L, request, context)).thenReturn(new AgentCommentCreateResponse(10L));
        service.createComment(1L, KEY, 2L, request, context);
        service.createReply(2L, KEY, 9L, request, context);
        assertThat(service.createComment(1L, KEY, 2L, request, context).getCommentId()).isEqualTo(9);
        assertThat(service.createReply(2L, KEY, 9L, request, context).getCommentId()).isEqualTo(10);
        verify(commands, times(1)).createComment(any(), any(), any(), any());
        verify(commands, times(1)).createReply(any(), any(), any(), any());
    }

    @Test
    void noteReplayPreservesOriginalSendTimeAndIds() {
        var request = new AgentNoteSendRequest();
        ReflectionTestUtils.setField(request, "recipientAgentName", "recipient");
        ReflectionTestUtils.setField(request, "content", "private note");
        OffsetDateTime sentAt = OffsetDateTime.parse("2026-10-16T09:00:00+09:00");
        when(notes.sendNote(1L, request, context)).thenReturn(AgentNoteResponses.SendResponse.builder()
                .status("sent").noteId(11L).noteThreadId(12L).sentAt(sentAt).build());
        service.sendNote(1L, KEY, request, context);
        var replay = service.sendNote(1L, KEY, request, context);
        assertThat(replay.getNoteId()).isEqualTo(11);
        assertThat(replay.getNoteThreadId()).isEqualTo(12);
        assertThat(replay.getStatus()).isEqualTo("sent");
        assertThat(replay.getSentAt()).isEqualTo(sentAt);
        verify(notes, times(1)).sendNote(any(), any(), any());
        assertThat(stored.values().iterator().next().getResponseJson()).doesNotContain("private note", "recipient");
    }

    @Test
    void sameIdIsScopedToAuthenticatedAgent() {
        when(commands.createPost(anyLong(), any(), any())).thenReturn(new AgentPostCreateResponse(42L, "/posts/42"));
        service.createPost(1L, KEY, post("one"), context);
        assertCode(() -> service.getOperation(2L, KEY), ErrorCode.AGENT_OPERATION_NOT_FOUND);
        service.createPost(2L, KEY, post("two"), context);
        assertThat(stored).hasSize(2);
        var lookup = service.getOperation(1L, KEY);
        assertThat(lookup.status()).isEqualTo("succeeded");
        assertThat(lookup.action()).isEqualTo("create_post");
        assertThat(lookup.result().path("postId").asLong()).isEqualTo(42);
        assertThat(lookup.completedAt()).isEqualTo(now);
        assertThat(JsonMapper.builder().build().writeValueAsString(lookup))
                .contains("\"completed_at\":\"2026-10-16T00:00:00Z\"");
    }

    @Test
    void invalidIdRejectedBeforeIdentityLookupOrWrite() {
        for (String key : new String[]{null, "", "../posts", KEY.toUpperCase(), "1-1-1-1-1"}) {
            assertCode(() -> service.createPost(1L, key, post("one"), context), ErrorCode.INVALID_INPUT_VALUE);
            assertCode(() -> service.getOperation(1L, key), ErrorCode.INVALID_INPUT_VALUE);
        }
        verifyNoInteractions(ownership, commands, notes);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectedCommandLeavesNoReceiptAndSameIdMayBeRetried() {
        when(commands.createPost(anyLong(), any(), any())).thenThrow(new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED));
        assertCode(() -> service.createPost(1L, KEY, post("one"), context), ErrorCode.RATE_LIMIT_EXCEEDED);
        assertThat(stored).isEmpty();
        assertCode(() -> service.getOperation(1L, KEY), ErrorCode.AGENT_OPERATION_NOT_FOUND);
        doReturn(new AgentPostCreateResponse(42L, "/posts/42")).when(commands).createPost(anyLong(), any(), any());
        assertThat(service.createPost(1L, KEY, post("one"), context).getPostId()).isEqualTo(42);
    }

    private static AgentPostCreateRequest post(String title) {
        var request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", title);
        ReflectionTestUtils.setField(request, "content", "private content");
        return request;
    }
    private static AgentCommentCreateRequest comment() {
        var request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "comment body");
        return request;
    }
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
