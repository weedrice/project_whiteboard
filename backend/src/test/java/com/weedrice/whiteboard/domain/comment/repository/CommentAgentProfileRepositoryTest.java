package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class CommentAgentProfileRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private CommentRepository commentRepository;

    private User owner;
    private Agent agent;
    private Board publicBoard;

    @BeforeEach
    void setUp() {
        owner = entityManager.persist(User.builder().loginId("profile-owner").email("profile@test.com")
                .password("password").displayName("Profile Owner").build());
        agent = persistAgent("profile-agent");
        publicBoard = persistBoard("public", true, true, true);
    }

    @ParameterizedTest(name = "exclude {0}")
    @ValueSource(strings = { "blindedComment", "blindedPost", "deletedComment", "deletedPost", "secretPost",
            "privateBoard", "inactiveBoard", "unlistedBoard", "agentDisabledBoard", "otherAgent" })
    void profileComments_filterBeforePaginationAndUseSameVisibilityForCountAndLikes(String restriction) {
        Post visiblePost = persistPost(publicBoard, false);
        Comment first = persistComment(visiblePost, agent, 2);
        Comment second = persistComment(visiblePost, agent, 3);
        Board hiddenBoard = switch (restriction) {
            case "privateBoard" -> persistBoard("private", false, false, true);
            case "unlistedBoard" -> persistBoard("unlisted", true, false, true);
            case "agentDisabledBoard" -> persistBoard("agent-disabled", true, true, false);
            case "inactiveBoard" -> {
                Board inactive = persistBoard("inactive", true, true, true);
                inactive.deactivate();
                yield inactive;
            }
            default -> publicBoard;
        };
        Post hiddenPost = persistPost(hiddenBoard, "secretPost".equals(restriction));
        if ("blindedPost".equals(restriction)) hiddenPost.blind("test", LocalDateTime.now());
        if ("deletedPost".equals(restriction)) hiddenPost.deletePost();
        Comment hiddenComment = persistComment(hiddenPost,
                "otherAgent".equals(restriction) ? persistAgent("other-agent") : agent, 20);
        if ("blindedComment".equals(restriction)) hiddenComment.blind("test", LocalDateTime.now());
        if ("deletedComment".equals(restriction)) hiddenComment.deleteComment();
        entityManager.flush();
        // Equal timestamps exercise the commentId tie-breaker; the hidden row is the newest ID.
        entityManager.getEntityManager().createQuery("UPDATE Comment c SET c.createdAt = :createdAt")
                .setParameter("createdAt", LocalDateTime.of(2030, 1, 1, 0, 0)).executeUpdate();
        entityManager.clear();

        Page<Comment> firstPage = commentRepository.findPublicProfileCommentsByAgentId(
                agent.getAgentId(), PageRequest.of(0, 1));
        Page<Comment> secondPage = commentRepository.findPublicProfileCommentsByAgentId(
                agent.getAgentId(), PageRequest.of(1, 1));
        assertThat(firstPage.getContent()).extracting(Comment::getCommentId).containsExactly(second.getCommentId());
        assertThat(secondPage.getContent()).extracting(Comment::getCommentId).containsExactly(first.getCommentId());
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(commentRepository.countPublicProfileCommentsByAgentId(agent.getAgentId())).isEqualTo(2);
        assertThat(commentRepository.sumPublicProfileCommentLikesByAgentId(agent.getAgentId())).isEqualTo(5);
    }

    @ParameterizedTest(name = "blind parent post: {0}")
    @ValueSource(booleans = { false, true })
    void profileComments_reflectBlindAndUnblindWithoutDeletingContent(boolean blindPost) {
        Post post = persistPost(publicBoard, false);
        Comment comment = persistComment(post, agent, 4);
        Long postId = post.getPostId();
        Long commentId = comment.getCommentId();
        if (blindPost) post.blind("test", LocalDateTime.now());
        else comment.blind("test", LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();
        assertThat(commentRepository.findPublicProfileCommentsByAgentId(agent.getAgentId(), PageRequest.of(0, 5)))
                .isEmpty();
        assertThat(commentRepository.countPublicProfileCommentsByAgentId(agent.getAgentId())).isZero();
        assertThat(commentRepository.sumPublicProfileCommentLikesByAgentId(agent.getAgentId())).isZero();

        if (blindPost) entityManager.find(Post.class, postId).unblind();
        else entityManager.find(Comment.class, commentId).unblind();
        entityManager.flush();
        entityManager.clear();
        assertThat(commentRepository.findPublicProfileCommentsByAgentId(agent.getAgentId(), PageRequest.of(0, 5)))
                .extracting(Comment::getCommentId).containsExactly(commentId);
        assertThat(commentRepository.countPublicProfileCommentsByAgentId(agent.getAgentId())).isEqualTo(1);
        assertThat(commentRepository.sumPublicProfileCommentLikesByAgentId(agent.getAgentId())).isEqualTo(4);
    }

    private Agent persistAgent(String name) {
        return entityManager.persist(Agent.builder().user(owner).name(name).description("Test agent")
                .agentTokenHash(name + "-hash").status(Agent.STATUS_ACTIVE).build());
    }

    private Board persistBoard(String url, boolean isPublic, boolean isListed, boolean agentEnabled) {
        return entityManager.persist(Board.builder().boardName(url).boardUrl(url).creator(owner)
                .isPublic(isPublic).isListed(isListed).agentUseYn(agentEnabled).build());
    }

    private Post persistPost(Board board, boolean secret) {
        return entityManager.persist(Post.builder().board(board).user(owner)
                .title("Post").contents("Post contents").isSecret(secret).build());
    }

    private Comment persistComment(Post post, Agent author, int likes) {
        Comment comment = Comment.builder().post(post).user(owner).agent(author)
                .content("Profile comment").depth(0).build();
        for (int i = 0; i < likes; i++) comment.incrementLikeCount();
        return entityManager.persist(comment);
    }
}
