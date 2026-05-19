package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentNote;
import com.weedrice.whiteboard.domain.agent.entity.AgentNoteThread;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class AgentNoteRepositoryTest {

    @Autowired
    private AgentNoteRepository agentNoteRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findLatestVisibleInThreads_returnsLatestVisibleNotePerThread() {
        Agent owner = persistAgent("owner");
        Agent counterpart = persistAgent("counterpart");
        Agent other = persistAgent("other");
        AgentNoteThread firstThread = persistThread(owner, counterpart);
        AgentNoteThread secondThread = persistThread(owner, other);
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 5, 19, 10, 0);
        AgentNote firstOlder = persistNote(firstThread, counterpart, owner, "first older");
        AgentNote firstLatest = persistNote(firstThread, owner, counterpart, "first latest");
        AgentNote hiddenNewest = persistNote(firstThread, counterpart, owner, "hidden newest");
        AgentNote secondLatest = persistNote(secondThread, other, owner, "second latest");
        entityManager.flush();

        updateCreatedAt(firstOlder, sameCreatedAt);
        updateCreatedAt(firstLatest, sameCreatedAt);
        updateCreatedAt(hiddenNewest, sameCreatedAt.plusMinutes(2));
        updateCreatedAt(secondLatest, sameCreatedAt.plusMinutes(1));
        hideByReceiver(hiddenNewest);
        entityManager.flush();
        entityManager.clear();

        List<AgentNote> result = agentNoteRepository.findLatestVisibleInThreads(
                List.of(firstThread.getNoteThreadId(), secondThread.getNoteThreadId()),
                owner.getAgentId());

        assertThat(result)
                .extracting(AgentNote::getNoteId)
                .containsExactly(secondLatest.getNoteId(), firstLatest.getNoteId());
    }

    @Test
    void countUnreadNotesByThreadIds_groupsUnreadReceiverNotes() {
        Agent owner = persistAgent("unread-owner");
        Agent counterpart = persistAgent("unread-counterpart");
        Agent other = persistAgent("unread-other");
        AgentNoteThread thread = persistThread(owner, counterpart);
        AgentNoteThread secondThread = persistThread(owner, other);
        AgentNoteThread emptyThread = persistThread(counterpart, other);
        persistNote(thread, counterpart, owner, "unread");
        AgentNote readNote = persistNote(thread, counterpart, owner, "read");
        readNote.markAsRead();
        AgentNote hiddenNote = persistNote(thread, counterpart, owner, "hidden");
        persistNote(thread, owner, counterpart, "sent by owner");
        persistNote(secondThread, other, owner, "second unread");
        persistNote(secondThread, other, owner, "second another unread");
        persistNote(emptyThread, counterpart, other, "not owner thread");
        entityManager.flush();

        hideByReceiver(hiddenNote);
        entityManager.flush();
        entityManager.clear();

        List<AgentNoteRepository.ThreadUnreadCountProjection> result =
                agentNoteRepository.countUnreadNotesByThreadIds(
                        List.of(thread.getNoteThreadId(), secondThread.getNoteThreadId(), emptyThread.getNoteThreadId()),
                        owner.getAgentId());

        assertThat(result)
                .extracting(
                        AgentNoteRepository.ThreadUnreadCountProjection::getNoteThreadId,
                        AgentNoteRepository.ThreadUnreadCountProjection::getUnreadCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(thread.getNoteThreadId(), 1L),
                        org.assertj.core.groups.Tuple.tuple(secondThread.getNoteThreadId(), 2L));
    }

    private Agent persistAgent(String name) {
        User user = User.builder()
                .loginId(name + "-user")
                .password("password")
                .email(name + "@example.com")
                .displayName(name)
                .build();
        entityManager.persist(user);
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash(name + "-token")
                .name(name)
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        entityManager.persist(agent);
        return agent;
    }

    private AgentNoteThread persistThread(Agent firstAgent, Agent secondAgent) {
        AgentNoteThread thread = new AgentNoteThread(firstAgent, secondAgent);
        entityManager.persist(thread);
        return thread;
    }

    private AgentNote persistNote(AgentNoteThread thread, Agent sender, Agent receiver, String content) {
        AgentNote note = new AgentNote(thread, sender, receiver, content);
        entityManager.persist(note);
        return note;
    }

    private void updateCreatedAt(AgentNote note, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE agent_notes SET created_at = :createdAt WHERE note_id = :noteId")
                .setParameter("createdAt", createdAt)
                .setParameter("noteId", note.getNoteId())
                .executeUpdate();
    }

    private void hideByReceiver(AgentNote note) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE agent_notes SET is_hidden_by_receiver = 'Y' WHERE note_id = :noteId")
                .setParameter("noteId", note.getNoteId())
                .executeUpdate();
    }
}
