package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.dto.PollResponse;
import com.weedrice.whiteboard.domain.post.entity.Poll;
import com.weedrice.whiteboard.domain.post.entity.PollOption;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.port.PostUserWritePort;
import com.weedrice.whiteboard.domain.post.repository.PollRepository;
import com.weedrice.whiteboard.domain.post.repository.PollVoteRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(QuerydslConfig.class)
class PollServicePersistenceTest {

    @Autowired private EntityManager entityManager;
    @Autowired private PollRepository pollRepository;
    @Autowired private PollVoteRepository pollVoteRepository;

    private PollService service;
    private Long userId;
    private Long postId;
    private Long pollId;
    private Long firstOptionId;
    private Long secondOptionId;

    @BeforeEach
    void setUp() {
        // Hibernate-generated test schemas omit the unique constraint introduced by V33.
        entityManager.createNativeQuery("""
                ALTER TABLE poll_votes ADD CONSTRAINT IF NOT EXISTS uk_poll_votes_user_option
                UNIQUE (poll_id, user_id, option_id)
                """).executeUpdate();
        User user = User.builder()
                .loginId("poll-persistence").email("poll-persistence@example.com")
                .password("test-password").displayName("Poll tester").build();
        entityManager.persist(user);
        Board board = Board.builder()
                .boardName("Poll tests").boardUrl("poll-persistence").creator(user).build();
        entityManager.persist(board);
        Post post = Post.builder().title("Poll post").contents("Content").user(user).board(board).build();
        entityManager.persist(post);
        Poll poll = Poll.builder().post(post).question("Choose options").multipleChoiceEnabled(true).build();
        PollOption first = PollOption.builder().poll(poll).optionText("First").sortOrder(0).build();
        PollOption second = PollOption.builder().poll(poll).optionText("Second").sortOrder(1).build();
        poll.addOption(first);
        poll.addOption(second);
        pollRepository.saveAndFlush(poll);
        userId = user.getUserId();
        postId = post.getPostId();
        pollId = poll.getPollId();
        firstOptionId = first.getOptionId();
        secondOptionId = second.getOptionId();

        PostUserWritePort users = mock(PostUserWritePort.class);
        when(users.validateContentWriteForUpdate(userId)).thenReturn(user);
        PostReadContextResolver contexts = mock(PostReadContextResolver.class);
        PostReadContext context = PostReadContext.anonymous();
        when(contexts.resolveForResolvedUser(any())).thenReturn(context);
        when(contexts.withAdminBoardIdsForPosts(eq(context), anyList())).thenReturn(context);
        service = new PollService(pollRepository, pollVoteRepository, users, contexts,
                mock(PostAccessPolicy.class), mock(InquiryLegacyWritePolicy.class), Clock.systemUTC());
    }

    @Test
    void repeatingSelectionKeepsExactlyOneVote() {
        castInitialVote();

        PollResponse response = service.vote(userId, postId, List.of(firstOptionId));

        assertSelection(response, List.of(firstOptionId));
    }

    @Test
    void expandingSelectionRetainsExistingOptionWithoutDuplicateConflict() {
        castInitialVote();

        PollResponse response = service.vote(userId, postId, List.of(firstOptionId, secondOptionId));

        assertSelection(response, List.of(firstOptionId, secondOptionId));
    }

    @Test
    void replacingSelectionRemovesPreviousVote() {
        castInitialVote();

        PollResponse response = service.vote(userId, postId, List.of(secondOptionId));

        assertSelection(response, List.of(secondOptionId));
        assertThat(response.getOptions().stream()
                .filter(option -> option.getOptionId().equals(firstOptionId))
                .findFirst().orElseThrow().getVoteCount()).isZero();
    }

    private void castInitialVote() {
        service.vote(userId, postId, List.of(firstOptionId));
        entityManager.flush();
        entityManager.clear();
    }

    private void assertSelection(PollResponse response, List<Long> selectedOptionIds) {
        entityManager.flush();
        entityManager.clear();
        assertThat(pollVoteRepository.findSelectedOptionIds(pollId, userId))
                .containsExactlyInAnyOrderElementsOf(selectedOptionIds);
        assertThat(response.getOptions().stream().filter(PollResponse.Option::isSelected)
                .map(PollResponse.Option::getOptionId).toList())
                .containsExactlyInAnyOrderElementsOf(selectedOptionIds);
        assertThat(response.getOptions().stream().filter(PollResponse.Option::isSelected)
                .map(PollResponse.Option::getVoteCount).toList())
                .allMatch(count -> count == 1L);
    }
}
