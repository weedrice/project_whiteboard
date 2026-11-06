package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class AgentPostActivityVisibilityTest {
    @Autowired private TestEntityManager em;
    @Autowired private CommentRepository comments;
    private User owner;
    private User responder;
    private Board board;
    private Agent agent;
    private Post post;

    @BeforeEach
    void setUp() {
        owner = user("owner");
        responder = user("responder");
        board = em.persist(Board.builder().boardName("Activity").boardUrl("activity")
                .creator(owner).agentUseYn(true).build());
        agent = em.persist(Agent.builder().name("Activity agent").description("Activity visibility test").agentTokenHash("activity-token")
                .user(owner).status(Agent.STATUS_ACTIVE).build());
        post = em.persist(Post.builder().title("Own post").contents("Body").user(owner).agent(agent).board(board).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BAN", "PERMANENT_BAN", "SUSPENDED", "DELETED", "BLINDED", "OUTGOING_BLOCK", "INCOMING_BLOCK",
            "EXPIRED_BAN", "FUTURE_BAN", "MUTE"})
    void selectsNewestVisibleCommentAndUsesSameEligibilityForBothCounts(String state) {
        User visible = user("visible");
        Comment observed = comment(post, visible, "Visible older", 1);
        Comment newer = comment(post, responder, "Candidate newer", 2);
        switch (state) {
            case "SUSPENDED" -> ReflectionTestUtils.setField(responder, "status", User.STATUS_SUSPENDED);
            case "DELETED" -> ReflectionTestUtils.setField(responder, "deletedAt", LocalDateTime.now());
            case "BLINDED" -> ReflectionTestUtils.setField(newer, "isBlinded", true);
            case "OUTGOING_BLOCK" -> em.persist(UserBlock.builder().user(owner).target(responder).build());
            case "INCOMING_BLOCK" -> em.persist(UserBlock.builder().user(responder).target(owner).build());
            default -> {
                LocalDateTime now = LocalDateTime.now();
                em.persist(Sanction.builder().targetUser(responder).processorUser(owner)
                        .type(state.equals("MUTE") ? "MUTE" : "ban")
                        .startDate(state.equals("FUTURE_BAN") ? now.plusDays(1) : now.minusDays(2))
                        .endDate(state.equals("PERMANENT_BAN") ? null
                                : state.equals("EXPIRED_BAN") ? now.minusDays(1) : now.plusDays(2)).build());
            }
        }
        em.flush();
        em.clear();
        boolean visibleCandidate = state.equals("EXPIRED_BAN") || state.equals("FUTURE_BAN") || state.equals("MUTE");
        var activity = comments.findUnreadAgentPostActivities(agent.getAgentId(), true, List.of(-1L), PageRequest.of(0, 1));
        assertThat(activity).singleElement().satisfies(item -> {
            assertThat(item.getLatestCommentContent()).isEqualTo(visibleCandidate ? "Candidate newer" : "Visible older");
            assertThat(item.getUnreadCount()).isEqualTo(visibleCandidate ? 2L : 1L);
        });
        assertThat(comments.countUnreadCommentsOnAgentPost(agent.getAgentId(), post.getPostId(), true, List.of(-1L)))
                .isEqualTo(visibleCandidate ? 2L : 1L);
        // Hidden unobserved comments must not hold back the observed-activity read watermark.
        assertThat(comments.findFirstUnobservedActivityAt(agent.getAgentId(), post.getPostId(),
                true, List.of(-1L), LocalDateTime.now().plusMinutes(10), List.of(observed.getCommentId())).isPresent())
                .isEqualTo(visibleCandidate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETED_POST", "BLINDED_POST", "PRIVATE_BOARD", "INACTIVE_BOARD", "AGENT_DISABLED", "OWN_SECRET"})
    void sourceReadPolicyMatchesForActivityAndRemainingCount(String state) {
        comment(post, responder, "Activity", 1);
        switch (state) {
            case "DELETED_POST" -> ReflectionTestUtils.setField(post, "isDeleted", true);
            case "BLINDED_POST" -> ReflectionTestUtils.setField(post, "isBlinded", true);
            case "PRIVATE_BOARD" -> ReflectionTestUtils.setField(board, "isPublic", false);
            case "INACTIVE_BOARD" -> ReflectionTestUtils.setField(board, "isActive", false);
            case "AGENT_DISABLED" -> ReflectionTestUtils.setField(board, "agentUseYn", false);
            case "OWN_SECRET" -> ReflectionTestUtils.setField(post, "isSecret", true);
        }
        em.flush();
        em.clear();
        assertThat(comments.findUnreadAgentPostActivities(agent.getAgentId(), true, List.of(-1L), PageRequest.of(0, 1)))
                .hasSize(state.equals("OWN_SECRET") ? 1 : 0);
        assertThat(comments.countUnreadCommentsOnAgentPost(agent.getAgentId(), post.getPostId(), true, List.of(-1L)))
                .isEqualTo(state.equals("OWN_SECRET") ? 1L : 0L);
        assertThat(comments.findFirstUnobservedActivityAt(agent.getAgentId(), post.getPostId(),
                true, List.of(-1L), LocalDateTime.now().plusMinutes(10), List.of(-1L)).isPresent())
                .isEqualTo(state.equals("OWN_SECRET"));
    }

    private User user(String name) {
        return em.persist(User.builder().loginId(name).email(name + "@activity.test").password("pw").displayName(name).build());
    }

    private Comment comment(Post source, User author, String body, int minutes) {
        Comment result = em.persist(Comment.builder().post(source).user(author).content(body).depth(0).build());
        em.flush();
        LocalDateTime created = LocalDateTime.now().plusMinutes(minutes);
        em.getEntityManager().createNativeQuery("UPDATE comments SET created_at = :created WHERE comment_id = :id")
                .setParameter("created", created).setParameter("id", result.getCommentId()).executeUpdate();
        return result;
    }
}
