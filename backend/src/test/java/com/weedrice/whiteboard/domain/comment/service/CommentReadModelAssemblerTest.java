package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CommentReadModelAssemblerTest {

    @Mock
    private CommentRepository commentRepository;

    private CommentReadModelAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new CommentReadModelAssembler(new CommentReadSupport(commentRepository));
    }

    @Test
    void from_activeUserComment_buildsUserAuthor() {
        User user = user(1L, "writer", "profile.png");
        Comment comment = comment(10L, user, null);

        CommentReadModel model = assembler.from(comment, Set.of(), Map.of(10L, 2L));

        assertThat(model.status()).isEqualTo(CommentReadModel.Status.ACTIVE);
        assertThat(model.replyCount()).isEqualTo(2L);
        assertThat(model.hasReplies()).isTrue();
        assertThat(model.author().userId()).isEqualTo(1L);
        assertThat(model.author().agentId()).isNull();
        assertThat(model.author().authorType()).isEqualTo("USER");
        assertThat(model.author().displayName()).isEqualTo("writer");
        assertThat(model.author().profileImageUrl()).isEqualTo("profile.png");
    }

    @Test
    void from_activeAgentComment_buildsAgentAuthor() {
        User user = user(1L, "owner", "profile.png");
        Agent agent = agent(7L, user, "agent-writer");
        Comment comment = comment(10L, user, agent);

        CommentReadModel model = assembler.from(comment, Set.of(), Map.of());

        assertThat(model.status()).isEqualTo(CommentReadModel.Status.ACTIVE);
        assertThat(model.replyCount()).isZero();
        assertThat(model.hasReplies()).isFalse();
        assertThat(model.author().userId()).isEqualTo(1L);
        assertThat(model.author().agentId()).isEqualTo(7L);
        assertThat(model.author().authorType()).isEqualTo("AGENT");
        assertThat(model.author().displayName()).isEqualTo("agent-writer");
        assertThat(model.author().profileImageUrl()).isNull();
    }

    @Test
    void from_blockedAuthor_masksAuthor() {
        User user = user(1L, "blocked", "profile.png");
        Comment comment = comment(10L, user, null);

        CommentReadModel model = assembler.from(comment, Set.of(1L), Map.of(10L, 1L));

        assertThat(model.status()).isEqualTo(CommentReadModel.Status.BLOCKED_AUTHOR);
        assertThat(model.author()).isNull();
        assertThat(model.maskedAuthorId()).isEqualTo(1L);
        assertThat(model.replyCount()).isEqualTo(1L);
        assertThat(model.hasReplies()).isTrue();
    }

    @Test
    void from_deletedComment_winsOverBlockedAuthor() {
        User user = user(1L, "deleted", "profile.png");
        Comment comment = comment(10L, user, null);
        comment.deleteComment();

        CommentReadModel model = assembler.from(comment, Set.of(1L), Map.of(10L, 1L));

        assertThat(model.status()).isEqualTo(CommentReadModel.Status.DELETED);
        assertThat(model.author()).isNull();
        assertThat(model.maskedAuthorId()).isNull();
        assertThat(model.replyCount()).isEqualTo(1L);
        assertThat(model.hasReplies()).isTrue();
    }

    private Comment comment(Long commentId, User user, Agent agent) {
        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 100L);
        Post post = Post.builder()
                .board(board)
                .user(user)
                .title("title")
                .contents("content")
                .build();
        ReflectionTestUtils.setField(post, "postId", 200L);
        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .agent(agent)
                .depth(0)
                .content("comment")
                .build();
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        return comment;
    }

    private User user(Long userId, String displayName, String profileImageUrl) {
        User user = User.builder()
                .loginId("user-" + userId)
                .displayName(displayName)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "profileImageUrl", profileImageUrl);
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
