package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
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

import java.time.LocalDateTime;
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
    @DisplayName("홈 랜딩 댓글 수는 공개 랜딩 노출 게시글의 기간 내 미삭제 댓글만 집계한다")
    void countPublicLandingVisibleCommentsCreatedBetween_countsOnlyVisibleCommentsInRange() {
        LocalDateTime todayStart = LocalDateTime.of(2026, 5, 4, 0, 0);
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        Board privateBoard = persistBoard("Landing Comment Private Board", "landing-comment-private-board", false);
        Board inactiveBoard = persistBoard("Landing Comment Inactive Board", "landing-comment-inactive-board", true);
        inactiveBoard.deactivate();
        Board inquiryBoard = persistBoard("Landing Comment Inquiry Board", "inquiry", true);
        Post secretPost = persistPost("Landing Secret Post", board, true, false);
        Post deletedPost = persistPost("Landing Deleted Post", board, false, true);
        Post privatePost = persistPost("Landing Private Post", privateBoard, false, false);
        Post inactivePost = persistPost("Landing Inactive Post", inactiveBoard, false, false);
        Post inquiryPost = persistPost("Landing Inquiry Post", inquiryBoard, false, false);

        Comment visibleComment = commentFor(post, "Visible Comment");
        Comment yesterdayComment = commentFor(post, "Yesterday Comment");
        Comment deletedComment = commentFor(post, "Deleted Comment");
        deletedComment.deleteComment();
        Comment secretPostComment = commentFor(secretPost, "Secret Post Comment");
        Comment deletedPostComment = commentFor(deletedPost, "Deleted Post Comment");
        Comment privatePostComment = commentFor(privatePost, "Private Post Comment");
        Comment inactivePostComment = commentFor(inactivePost, "Inactive Post Comment");
        Comment inquiryPostComment = commentFor(inquiryPost, "Inquiry Post Comment");
        entityManager.persist(visibleComment);
        entityManager.persist(yesterdayComment);
        entityManager.persist(deletedComment);
        entityManager.persist(secretPostComment);
        entityManager.persist(deletedPostComment);
        entityManager.persist(privatePostComment);
        entityManager.persist(inactivePostComment);
        entityManager.persist(inquiryPostComment);
        entityManager.flush();

        updateCreatedAt(comment, todayStart.minusDays(1));
        updateCreatedAt(visibleComment, todayStart.plusHours(1));
        updateCreatedAt(yesterdayComment, todayStart.minusHours(1));
        updateCreatedAt(deletedComment, todayStart.plusHours(2));
        updateCreatedAt(secretPostComment, todayStart.plusHours(3));
        updateCreatedAt(deletedPostComment, todayStart.plusHours(4));
        updateCreatedAt(privatePostComment, todayStart.plusHours(5));
        updateCreatedAt(inactivePostComment, todayStart.plusHours(6));
        updateCreatedAt(inquiryPostComment, todayStart.plusHours(7));
        entityManager.flush();
        entityManager.clear();

        long count = commentRepository.countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                todayStart,
                tomorrowStart);

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

    @Test
    @DisplayName("내 댓글 목록은 읽을 수 없는 게시글의 댓글을 제외하고 페이징 집계를 맞춘다")
    void findVisibleMyComments_filtersUnreadablePostsAndMatchesTotal() {
        User author = persistUser("author", "author@test.com", "Author");
        Board publicBoard = persistBoard("My Comment Public Board", "my-comment-public-board", true, author);
        Board privateBoard = persistBoard("My Comment Private Board", "my-comment-private-board", false, author);
        Board inactiveBoard = persistBoard("My Comment Inactive Board", "my-comment-inactive-board", true, author);
        inactiveBoard.deactivate();
        Board inquiryBoard = persistBoard("My Comment Inquiry Board", "inquiry", false, author);

        Post visiblePost = persistPost("Visible Post", publicBoard, false, false, author);
        Post deletedPost = persistPost("Deleted Post", publicBoard, false, true, author);
        Post privatePost = persistPost("Private Post", privateBoard, false, false, author);
        Post inactivePost = persistPost("Inactive Post", inactiveBoard, false, false, author);
        Post secretPost = persistPost("Secret Post", publicBoard, true, false, author);
        Post ownSecretPost = persistPost("Own Secret Post", publicBoard, true, false, user);
        Post ownInquiryPost = persistPost("Own Inquiry Post", inquiryBoard, false, false, user);
        entityManager.persist(commentFor(visiblePost, "Visible Comment", user));
        entityManager.persist(commentFor(deletedPost, "Deleted Post Comment", user));
        entityManager.persist(commentFor(privatePost, "Private Post Comment", user));
        entityManager.persist(commentFor(inactivePost, "Inactive Post Comment", user));
        entityManager.persist(commentFor(secretPost, "Secret Post Comment", user));
        entityManager.persist(commentFor(ownSecretPost, "Own Secret Comment", user));
        entityManager.persist(commentFor(ownInquiryPost, "Own Inquiry Comment", user));
        entityManager.flush();
        entityManager.clear();

        Page<Comment> result = commentRepository.findVisibleMyComments(
                user,
                false,
                true,
                List.of(-1L),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(4L);
        assertThat(result.getContent())
                .extracting(comment -> comment.getPost().getTitle())
                .containsExactlyInAnyOrder("Test Post", "Visible Post", "Own Secret Post", "Own Inquiry Post");
    }

    @Test
    @DisplayName("내 댓글 목록은 차단한 게시글 작성자의 글을 제외한다")
    void findVisibleMyComments_excludesBlockedPostAuthors() {
        User blockedAuthor = persistUser("blocked-author", "blocked-author@test.com", "Blocked Author");
        Post blockedAuthorPost = persistPost("Blocked Author Post", board, false, false, blockedAuthor);
        entityManager.persist(commentFor(blockedAuthorPost, "Blocked Author Comment", user));
        entityManager.flush();
        entityManager.clear();

        Page<Comment> result = commentRepository.findVisibleMyComments(
                user,
                false,
                false,
                List.of(blockedAuthor.getUserId()),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(comment -> comment.getPost().getTitle())
                .doesNotContain("Blocked Author Post");
    }

    @Test
    @DisplayName("내 댓글 목록은 게시판 관리자에게 비공개 게시판 댓글을 노출한다")
    void findVisibleMyComments_allowsActiveBoardAdmin() {
        Board privateBoard = persistBoard("Managed Private Board", "managed-private-board", false);
        entityManager.persist(Admin.builder()
                .user(user)
                .board(privateBoard)
                .role("MANAGER")
                .build());
        User author = persistUser("managed-author", "managed-author@test.com", "Managed Author");
        Post privatePost = persistPost("Managed Private Post", privateBoard, false, false, author);
        entityManager.persist(commentFor(privatePost, "Managed Private Comment", user));
        entityManager.flush();
        entityManager.clear();

        Page<Comment> result = commentRepository.findVisibleMyComments(
                user,
                false,
                true,
                List.of(-1L),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(comment -> comment.getPost().getTitle())
                .contains("Managed Private Post");
    }

    private Comment commentFor(Post post, String content) {
        return commentFor(post, content, user);
    }

    private Comment commentFor(Post post, String content, User commentAuthor) {
        return Comment.builder()
                .content(content)
                .user(commentAuthor)
                .post(post)
                .depth(0)
                .build();
    }

    private User persistUser(String loginId, String email, String displayName) {
        User targetUser = User.builder()
                .loginId(loginId)
                .email(email)
                .password("password")
                .displayName(displayName)
                .build();
        entityManager.persist(targetUser);
        return targetUser;
    }

    private Board persistBoard(String boardName, String boardUrl, boolean isPublic) {
        return persistBoard(boardName, boardUrl, isPublic, user);
    }

    private Board persistBoard(String boardName, String boardUrl, boolean isPublic, User creator) {
        Board targetBoard = Board.builder()
                .boardName(boardName)
                .boardUrl(boardUrl)
                .creator(creator)
                .isPublic(isPublic)
                .build();
        entityManager.persist(targetBoard);
        return targetBoard;
    }

    private Post persistPost(String title, Board targetBoard, boolean isSecret, boolean isDeleted) {
        return persistPost(title, targetBoard, isSecret, isDeleted, user);
    }

    private Post persistPost(String title, Board targetBoard, boolean isSecret, boolean isDeleted, User author) {
        Post targetPost = Post.builder()
                .title(title)
                .contents("Contents")
                .user(author)
                .board(targetBoard)
                .isSecret(isSecret)
                .build();
        if (isDeleted) {
            targetPost.deletePost();
        }
        entityManager.persist(targetPost);
        return targetPost;
    }

    private void updateCreatedAt(Comment targetComment, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE comments SET created_at = :createdAt WHERE comment_id = :commentId")
                .setParameter("createdAt", createdAt)
                .setParameter("commentId", targetComment.getCommentId())
                .executeUpdate();
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
