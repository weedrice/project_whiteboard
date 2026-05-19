package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteSendRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentNote;
import com.weedrice.whiteboard.domain.agent.entity.AgentNoteThread;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteException;
import com.weedrice.whiteboard.domain.agent.repository.AgentNoteRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentNoteThreadRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentNoteServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentNoteThreadRepository agentNoteThreadRepository;
    @Mock private AgentNoteRepository agentNoteRepository;
    @Mock private AgentOwnershipService agentOwnershipService;
    @Mock private AgentQuotaService agentQuotaService;
    @Mock private AgentPolicyService agentPolicyService;
    @Mock private AgentAuditService agentAuditService;
    @Mock private UserBlockService userBlockService;

    private AgentNoteService agentNoteService;
    private User senderUser;
    private User receiverUser;
    private Agent sender;
    private Agent receiver;

    @BeforeEach
    void setUp() {
        agentNoteService = new AgentNoteService(
                agentRepository,
                agentNoteThreadRepository,
                agentNoteRepository,
                agentOwnershipService,
                agentQuotaService,
                agentPolicyService,
                agentAuditService,
                userBlockService);

        senderUser = user(1L, "sender");
        receiverUser = user(2L, "receiver");
        sender = agent(7L, senderUser, "sender-agent");
        receiver = agent(8L, receiverUser, "receiver-agent");
    }

    @Test
    void sendNote_createsThreadAndReturnsMcpFriendlyResponse() {
        AgentNoteSendRequest request = noteRequest("receiver-agent", "hello");
        AgentPolicyService.AgentPolicySnapshot policy = policy(20);
        AgentNoteThread thread = new AgentNoteThread(sender, receiver);
        ReflectionTestUtils.setField(thread, "noteThreadId", 99L);
        LocalDateTime sentAt = LocalDateTime.now();

        when(agentOwnershipService.resolveActiveAgentForUpdate(7L)).thenReturn(sender);
        when(agentPolicyService.resolve(sender)).thenReturn(policy);
        when(agentRepository.findByNameAndIsDeletedFalse("receiver-agent")).thenReturn(Optional.of(receiver));
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        when(agentNoteThreadRepository.findByAgentPairForUpdate(7L, 8L)).thenReturn(Optional.empty());
        when(agentNoteThreadRepository.save(any(AgentNoteThread.class))).thenReturn(thread);
        when(agentNoteRepository.save(any(AgentNote.class))).thenAnswer(invocation -> {
            AgentNote note = invocation.getArgument(0);
            ReflectionTestUtils.setField(note, "noteId", 100L);
            ReflectionTestUtils.setField(note, "createdAt", sentAt);
            return note;
        });

        AgentNoteResponses.SendResponse response = agentNoteService.sendNote(7L, request, null);

        assertThat(response.getStatus()).isEqualTo("sent");
        assertThat(response.getNoteThreadId()).isEqualTo(99L);
        assertThat(response.getNoteId()).isEqualTo(100L);
        assertThat(response.getSentAt()).isEqualTo(sentAt.atZone(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime());
        verify(agentQuotaService).reserveNoteSend(sender);
        verify(agentAuditService).saveLog(
                sender,
                senderUser,
                AgentAuditActionType.SEND_NOTE,
                AgentAuditTargetType.NOTE,
                100L,
                null);
    }

    @Test
    void sendNote_rejectsSelfRecipient() {
        AgentNoteSendRequest request = noteRequest("sender-agent", "hello");
        when(agentOwnershipService.resolveActiveAgentForUpdate(7L)).thenReturn(sender);
        when(agentPolicyService.resolve(sender)).thenReturn(policy(20));
        when(agentRepository.findByNameAndIsDeletedFalse("sender-agent")).thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> agentNoteService.sendNote(7L, request, null))
                .isInstanceOf(AgentWriteException.class)
                .hasFieldOrPropertyWithValue("code", "note_self_send_forbidden");
    }

    @Test
    void markRead_isIdempotentAndReturnsRemainingUnreadCount() {
        AgentNoteThread thread = new AgentNoteThread(sender, receiver);
        ReflectionTestUtils.setField(thread, "noteThreadId", 99L);
        when(agentOwnershipService.resolveActiveAgent(7L)).thenReturn(sender);
        when(agentNoteThreadRepository.findByIdWithParticipants(99L)).thenReturn(Optional.of(thread));
        when(agentNoteRepository.markThreadReceivedNotesRead(99L, 7L)).thenReturn(0);
        when(agentNoteRepository.countUnreadNotesInThread(99L, 7L)).thenReturn(0L);

        AgentNoteResponses.ReadResponse response = agentNoteService.markRead(7L, 99L, null);

        assertThat(response.getNoteThreadId()).isEqualTo(99L);
        assertThat(response.isMarkedRead()).isTrue();
        assertThat(response.getRemainingUnreadCount()).isZero();
    }

    private AgentPolicyService.AgentPolicySnapshot policy(long notesRemaining) {
        AgentLimits limits = AgentLimits.builder()
                .maxNotesPerDay(AgentQuotaService.DAILY_AGENT_NOTE_LIMIT)
                .notesRemaining(notesRemaining)
                .nextNoteAllowedAt(notesRemaining == 0 ? OffsetDateTime.now() : null)
                .build();
        AgentRestrictions restrictions = AgentRestrictions.builder()
                .canSendNote(notesRemaining > 0)
                .build();
        return new AgentPolicyService.AgentPolicySnapshot(
                new AgentPolicyService.AgentDailyStatus(LocalDate.now(), 0, 0, OffsetDateTime.now()),
                limits,
                restrictions,
                false);
    }

    private AgentNoteSendRequest noteRequest(String recipientName, String content) {
        AgentNoteSendRequest request = new AgentNoteSendRequest();
        ReflectionTestUtils.setField(request, "recipientAgentName", recipientName);
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }

    private User user(Long userId, String displayName) {
        User user = User.builder().loginId(displayName).displayName(displayName).build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Agent agent(Long agentId, User user, String name) {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash-" + agentId)
                .name(name)
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", agentId);
        return agent;
    }
}
