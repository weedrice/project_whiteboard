package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.CommentClosure;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class CommentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CommentRepository commentRepository;

    private User user;
    private Board board;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .email("test@test.com")
                .password("password")
                .displayName("Test User")
                .build();
        entityManager.persist(user);

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(user)
                .build();
        entityManager.persist(board);

        post = Post.builder()
                .title("Test Post")
                .contents("Test Contents")
                .user(user)
                .board(board)
                .build();
        entityManager.persist(post);

        comment = Comment.builder()
                .content("Test Comment")
                .user(user)
                .post(post)
                .depth(0)
                .build();
        entityManager.persist(comment);
        entityManager.persist(CommentClosure.builder()
                .ancestor(comment)
                .descendant(comment)
                .depth(0)
                .build());
        entityManager.flush();
    }

    @Test
    @DisplayName("댓글 ID로 조회 성공")
    void findById_success() {
        // when
        Optional<Comment> found = commentRepository.findById(comment.getCommentId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("Test Comment");
    }

    @Test
    @DisplayName("게시글 ID로 댓글 목록 조회 성공")
    void findByPost_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Comment> comments = commentRepository.findByPost_PostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(
                post.getPostId(), false, pageRequest);

        // then
        assertThat(comments.getContent()).isNotEmpty();
        assertThat(comments.getContent().get(0).getPost()).isEqualTo(post);
    }

    @Test
    @DisplayName("공개 프로필 댓글 수는 공개 활성 게시글의 미삭제 댓글만 집계한다")
    void countPublicProfileCommentsByUser_countsOnlyPublicVisibleComments() {
        Board privateBoard = Board.builder()
                .boardName("Private Board")
                .boardUrl("private-board")
                .creator(user)
                .isPublic(false)
                .build();
        entityManager.persist(privateBoard);

        Board inactiveBoard = Board.builder()
                .boardName("Inactive Board")
                .boardUrl("inactive-board")
                .creator(user)
                .build();
        inactiveBoard.deactivate();
        entityManager.persist(inactiveBoard);

        Post secretPost = Post.builder()
                .title("Secret Post")
                .contents("Secret Contents")
                .user(user)
                .board(board)
                .isSecret(true)
                .build();
        entityManager.persist(secretPost);

        Post deletedPost = Post.builder()
                .title("Deleted Post")
                .contents("Deleted Contents")
                .user(user)
                .board(board)
                .build();
        deletedPost.deletePost();
        entityManager.persist(deletedPost);

        Post privateBoardPost = Post.builder()
                .title("Private Board Post")
                .contents("Private Contents")
                .user(user)
                .board(privateBoard)
                .build();
        entityManager.persist(privateBoardPost);

        Post inactiveBoardPost = Post.builder()
                .title("Inactive Board Post")
                .contents("Inactive Contents")
                .user(user)
                .board(inactiveBoard)
                .build();
        entityManager.persist(inactiveBoardPost);

        Comment deletedComment = Comment.builder()
                .content("Deleted Comment")
                .user(user)
                .post(post)
                .depth(0)
                .build();
        deletedComment.deleteComment();
        entityManager.persist(deletedComment);
        entityManager.persist(commentFor(secretPost, "Secret Post Comment"));
        entityManager.persist(commentFor(deletedPost, "Deleted Post Comment"));
        entityManager.persist(commentFor(privateBoardPost, "Private Board Comment"));
        entityManager.persist(commentFor(inactiveBoardPost, "Inactive Board Comment"));
        entityManager.flush();

        long count = commentRepository.countPublicProfileCommentsByUser(user);

        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("게시글별 비작성자 미삭제 댓글 존재 여부를 배치 조회한다")
    void findPostIdsWithNonAuthorCommentsByPostIds_success() {
        User responder = User.builder()
                .loginId("responder")
                .email("responder@test.com")
                .password("password")
                .displayName("Responder")
                .build();
        entityManager.persist(responder);

        Post secondPost = Post.builder()
                .title("Second Post")
                .contents("Second Contents")
                .user(user)
                .board(board)
                .build();
        entityManager.persist(secondPost);

        Comment olderComment = Comment.builder()
                .content("Older Comment")
                .user(user)
                .post(post)
                .depth(0)
                .build();
        entityManager.persist(olderComment);

        Comment responseComment = Comment.builder()
                .content("Response Comment")
                .user(responder)
                .post(post)
                .depth(0)
                .build();
        entityManager.persist(responseComment);

        Comment authorFollowUp = Comment.builder()
                .content("Author Follow-up")
                .user(user)
                .post(post)
                .depth(0)
                .build();
        entityManager.persist(authorFollowUp);

        Comment deletedResponseComment = Comment.builder()
                .content("Deleted Comment")
                .user(responder)
                .post(secondPost)
                .depth(0)
                .build();
        deletedResponseComment.deleteComment();
        entityManager.persist(deletedResponseComment);

        Comment authorOnlyComment = Comment.builder()
                .content("Author Only Comment")
                .user(user)
                .post(secondPost)
                .depth(0)
                .build();
        entityManager.persist(authorOnlyComment);

        entityManager.flush();

        List<Long> answeredPostIds = commentRepository
                .findPostIdsWithNonAuthorCommentsByPostIds(List.of(post.getPostId(), secondPost.getPostId()));

        assertThat(answeredPostIds).containsExactly(post.getPostId());
    }

    private Comment commentFor(Post post, String content) {
        return Comment.builder()
                .content(content)
                .user(user)
                .post(post)
                .depth(0)
                .build();
    }

    @Test
    @DisplayName("deleted parent with visible grandchild remains visible in parent and reply queries")
    void visibleQueries_includeDeletedChainWithVisibleGrandchild() {
        Comment deletedChild = Comment.builder()
                .content("Deleted Child")
                .user(user)
                .post(post)
                .parent(comment)
                .depth(1)
                .build();
        deletedChild.deleteComment();
        entityManager.persist(deletedChild);
        entityManager.persist(CommentClosure.builder()
                .ancestor(deletedChild)
                .descendant(deletedChild)
                .depth(0)
                .build());
        entityManager.persist(CommentClosure.builder()
                .ancestor(comment)
                .descendant(deletedChild)
                .depth(1)
                .build());

        Comment visibleGrandchild = Comment.builder()
                .content("Visible Grandchild")
                .user(user)
                .post(post)
                .parent(deletedChild)
                .depth(2)
                .build();
        entityManager.persist(visibleGrandchild);
        entityManager.persist(CommentClosure.builder()
                .ancestor(visibleGrandchild)
                .descendant(visibleGrandchild)
                .depth(0)
                .build());
        entityManager.persist(CommentClosure.builder()
                .ancestor(deletedChild)
                .descendant(visibleGrandchild)
                .depth(1)
                .build());
        entityManager.persist(CommentClosure.builder()
                .ancestor(comment)
                .descendant(visibleGrandchild)
                .depth(2)
                .build());
        entityManager.flush();
        entityManager.clear();

        Page<Comment> parents = commentRepository.findParentsWithChildrenOrNotDeleted(
                post.getPostId(),
                PageRequest.of(0, 10));
        Page<Comment> replies = commentRepository.findRepliesWithRelations(
                comment.getCommentId(),
                false,
                PageRequest.of(0, 10));
        List<CommentRepository.ReplyCountProjection> replyCounts =
                commentRepository.countVisibleRepliesByParentIds(List.of(comment.getCommentId(), deletedChild.getCommentId()));

        assertThat(parents.getContent())
                .extracting(Comment::getCommentId)
                .contains(comment.getCommentId());
        assertThat(replies.getContent())
                .extracting(Comment::getCommentId)
                .contains(deletedChild.getCommentId());
        assertThat(replyCounts)
                .extracting(CommentRepository.ReplyCountProjection::getParentId,
                        CommentRepository.ReplyCountProjection::getReplyCount)
                .contains(
                        org.assertj.core.groups.Tuple.tuple(comment.getCommentId(), 1L),
                        org.assertj.core.groups.Tuple.tuple(deletedChild.getCommentId(), 1L));
    }
}
