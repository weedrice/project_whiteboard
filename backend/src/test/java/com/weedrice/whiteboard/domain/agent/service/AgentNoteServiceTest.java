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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
        when(agentNoteThreadRepository.findByAgentPairForUpdate(7L, 8L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(thread));
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
        verify(agentNoteThreadRepository).insertIgnorePair(7L, 8L);
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
    void sendNote_usesExistingThreadWithoutInsertIgnore() {
        AgentNoteSendRequest request = noteRequest("receiver-agent", "hello");
        AgentPolicyService.AgentPolicySnapshot policy = policy(20);
        AgentNoteThread thread = new AgentNoteThread(sender, receiver);
        ReflectionTestUtils.setField(thread, "noteThreadId", 99L);
        LocalDateTime sentAt = LocalDateTime.now();

        when(agentOwnershipService.resolveActiveAgentForUpdate(7L)).thenReturn(sender);
        when(agentPolicyService.resolve(sender)).thenReturn(policy);
        when(agentRepository.findByNameAndIsDeletedFalse("receiver-agent")).thenReturn(Optional.of(receiver));
        when(userBlockService.isEitherDirectionBlocked(1L, 2L)).thenReturn(false);
        when(agentNoteThreadRepository.findByAgentPairForUpdate(7L, 8L)).thenReturn(Optional.of(thread));
        when(agentNoteRepository.save(any(AgentNote.class))).thenAnswer(invocation -> {
            AgentNote note = invocation.getArgument(0);
            ReflectionTestUtils.setField(note, "noteId", 100L);
            ReflectionTestUtils.setField(note, "createdAt", sentAt);
            return note;
        });

        AgentNoteResponses.SendResponse response = agentNoteService.sendNote(7L, request, null);

        assertThat(response.getNoteThreadId()).isEqualTo(99L);
        verify(agentNoteThreadRepository, never()).insertIgnorePair(any(), any());
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

    @Test
    void getNotes_usesBulkQueriesAndPreservesThreadOrder() {
        AgentNoteThread firstThread = thread(101L, sender, receiver);
        AgentNoteThread secondThread = thread(102L, sender, receiver);
        AgentNote firstLatest = note(firstThread, receiver, sender, 1001L, "first latest", LocalDateTime.now());
        AgentNote secondLatest = note(
                secondThread,
                sender,
                receiver,
                1002L,
                "second latest",
                LocalDateTime.now().plusMinutes(1));

        when(agentOwnershipService.resolveActiveAgent(7L)).thenReturn(sender);
        when(agentNoteThreadRepository.findInboxThreadIds(7L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(101L, 102L, 103L), PageRequest.of(0, 20), 3));
        when(agentNoteRepository.findLatestVisibleInThreads(List.of(101L, 102L, 103L), 7L))
                .thenReturn(List.of(secondLatest, firstLatest));
        when(agentNoteRepository.countUnreadNotesByThreadIds(List.of(101L, 102L, 103L), 7L))
                .thenReturn(List.of(unreadCount(101L, 2L)));

        AgentNoteResponses.ThreadListResponse response = agentNoteService.getNotes(7L, null, PageRequest.of(0, 20));

        assertThat(response.getContent())
                .extracting(AgentNoteResponses.ThreadListItem::getNoteThreadId)
                .containsExactly(101L, 102L);
        assertThat(response.getContent())
                .extracting(AgentNoteResponses.ThreadListItem::getLatestNoteId)
                .containsExactly(1001L, 1002L);
        assertThat(response.getContent())
                .extracting(AgentNoteResponses.ThreadListItem::getUnreadCount)
                .containsExactly(2L, 0L);
        verify(agentNoteRepository, never()).findLatestVisibleInThread(any(), any(), any());
        verify(agentNoteRepository, never()).countUnreadNotesInThread(any(), any());
    }

    @Test
    void getNotes_skipsBulkQueriesWhenThreadPageIsEmpty() {
        when(agentOwnershipService.resolveActiveAgent(7L)).thenReturn(sender);
        when(agentNoteThreadRepository.findInboxThreadIds(7L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        AgentNoteResponses.ThreadListResponse response = agentNoteService.getNotes(7L, null, PageRequest.of(0, 20));

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        verify(agentNoteRepository, never()).findLatestVisibleInThreads(any(), any());
        verify(agentNoteRepository, never()).countUnreadNotesByThreadIds(any(), any());
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

    private AgentNoteThread thread(Long threadId, Agent firstAgent, Agent secondAgent) {
        AgentNoteThread thread = new AgentNoteThread(firstAgent, secondAgent);
        ReflectionTestUtils.setField(thread, "noteThreadId", threadId);
        return thread;
    }

    private AgentNote note(AgentNoteThread thread, Agent senderAgent, Agent receiverAgent, Long noteId,
            String content, LocalDateTime createdAt) {
        AgentNote note = new AgentNote(thread, senderAgent, receiverAgent, content);
        ReflectionTestUtils.setField(note, "noteId", noteId);
        ReflectionTestUtils.setField(note, "createdAt", createdAt);
        return note;
    }

    private AgentNoteRepository.ThreadUnreadCountProjection unreadCount(Long noteThreadId, long unreadCount) {
        return new AgentNoteRepository.ThreadUnreadCountProjection() {
            @Override
            public Long getNoteThreadId() {
                return noteThreadId;
            }

            @Override
            public long getUnreadCount() {
                return unreadCount;
            }
        };
    }
}
