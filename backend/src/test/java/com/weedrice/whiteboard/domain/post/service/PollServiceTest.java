package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import com.weedrice.whiteboard.domain.post.entity.Poll;
import com.weedrice.whiteboard.domain.post.entity.PollOption;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PollRepository;
import com.weedrice.whiteboard.domain.post.repository.PollVoteRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PollServiceTest {

    @Mock PollRepository polls;
    @Mock PollVoteRepository votes;
    @Mock UserWritableResolver users;
    PollService service;

    @BeforeEach
    void setUp() {
        service = new PollService(polls, votes, users);
    }

    @Test
    void createsValidPollAndIgnoresMissingRequest() {
        Post post = mock(Post.class);
        service.createPoll(post, null);
        verify(polls, never()).save(any());

        PollRequest request = request(" Question ", List.of(" A ", " B "), false, null);
        service.createPoll(post, request);
        verify(polls).save(org.mockito.ArgumentMatchers.argThat(poll ->
                "Question".equals(poll.getQuestion()) && poll.getOptions().size() == 2));
    }

    @Test
    void rejectsMalformedPollRequests() {
        Post post = mock(Post.class);
        assertThrows(BusinessException.class, () -> service.createPoll(post, request(" ", List.of("a", "b"), false, null)));
        assertThrows(BusinessException.class, () -> service.createPoll(post, request("q", List.of("a"), false, null)));
        assertThrows(BusinessException.class, () -> service.createPoll(post, request("q", List.of("a", " "), false, null)));
    }

    @Test
    void getMissingPollReturnsNullAndPresentPollMapsVotes() {
        when(polls.findByPost_PostId(1L)).thenReturn(Optional.empty());
        assertNull(service.getPollResponse(1L, null));

        Poll poll = poll(false, null);
        when(polls.findByPost_PostId(2L)).thenReturn(Optional.of(poll));
        when(votes.countByOption(20L)).thenReturn(List.of());
        when(votes.findSelectedOptionIds(20L, 7L)).thenReturn(List.of(101L));
        assertEquals(2, service.getPollResponse(2L, 7L).getOptions().size());
    }

    @Test
    void voteReplacesPriorVotesAndDeduplicatesSelection() {
        User user = mock(User.class);
        Poll poll = poll(true, null);
        when(users.resolve(7L)).thenReturn(user);
        when(polls.findByPostIdForUpdate(2L)).thenReturn(Optional.of(poll));
        when(votes.countByOption(20L)).thenReturn(List.of());
        when(votes.findSelectedOptionIds(20L, 7L)).thenReturn(List.of(101L, 102L));

        var response = service.vote(7L, 2L, List.of(101L, 101L, 102L));

        assertEquals(2, response.getOptions().stream().filter(option -> option.isSelected()).count());
        verify(votes).deleteByPoll_PollIdAndUser_UserId(20L, 7L);
        verify(votes, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void voteRejectsMissingClosedEmptyMultipleAndForeignOptions() {
        when(polls.findByPostIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.vote(1L, 9L, List.of(1L)));

        Poll closed = poll(false, LocalDateTime.now().minusMinutes(1));
        when(polls.findByPostIdForUpdate(3L)).thenReturn(Optional.of(closed));
        assertThrows(BusinessException.class, () -> service.vote(1L, 3L, List.of(101L)));

        Poll single = poll(false, null);
        when(polls.findByPostIdForUpdate(4L)).thenReturn(Optional.of(single));
        assertThrows(BusinessException.class, () -> service.vote(1L, 4L, List.of()));
        assertThrows(BusinessException.class, () -> service.vote(1L, 4L, List.of(101L, 102L)));
        assertThrows(BusinessException.class, () -> service.vote(1L, 4L, List.of(999L)));
    }

    @Test
    void deleteVoteIsIdempotentForExistingPoll() {
        Poll poll = poll(false, null);
        when(polls.findByPostIdForUpdate(5L)).thenReturn(Optional.of(poll));
        when(votes.countByOption(20L)).thenReturn(List.of());
        when(votes.findSelectedOptionIds(20L, 1L)).thenReturn(List.of());

        service.deleteVote(1L, 5L);
        verify(votes).deleteByPoll_PollIdAndUser_UserId(20L, 1L);
    }

    private static PollRequest request(String question, List<String> options, boolean multiple, LocalDateTime closesAt) {
        PollRequest request = new PollRequest();
        request.setQuestion(question);
        request.setOptions(options);
        request.setMultipleChoiceEnabled(multiple);
        request.setAnonymousEnabled(false);
        request.setClosesAt(closesAt);
        return request;
    }

    private static Poll poll(boolean multiple, LocalDateTime closesAt) {
        PollOption first = mock(PollOption.class);
        PollOption second = mock(PollOption.class);
        when(first.getOptionId()).thenReturn(101L);
        when(first.getSortOrder()).thenReturn(0);
        when(second.getOptionId()).thenReturn(102L);
        when(second.getSortOrder()).thenReturn(1);
        Poll poll = mock(Poll.class);
        when(poll.getPollId()).thenReturn(20L);
        when(poll.getMultipleChoiceEnabled()).thenReturn(multiple);
        when(poll.getClosesAt()).thenReturn(closesAt);
        when(poll.getOptions()).thenReturn(List.of(first, second));
        return poll;
    }
}
