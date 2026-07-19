package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import com.weedrice.whiteboard.domain.post.dto.PollResponse;
import com.weedrice.whiteboard.domain.post.entity.Poll;
import com.weedrice.whiteboard.domain.post.entity.PollOption;
import com.weedrice.whiteboard.domain.post.entity.PollVote;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PollRepository;
import com.weedrice.whiteboard.domain.post.repository.PollVoteRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PollService {

    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;
    private final UserWritableResolver userWritableResolver;
    private final PostReadContextResolver postReadContextResolver;
    private final PostAccessPolicy postAccessPolicy;
    private final SanctionService sanctionService;
    private final Clock clock;

    @Transactional
    public void createPoll(Post post, PollRequest request) {
        if (request == null) {
            return;
        }
        validatePollRequest(request);
        Poll poll = Poll.builder()
                .post(post)
                .question(request.getQuestion().strip())
                .multipleChoiceEnabled(request.getMultipleChoiceEnabled())
                .anonymousEnabled(request.getAnonymousEnabled())
                .closesAt(request.getClosesAt())
                .build();
        int sortOrder = 0;
        for (String optionText : request.getOptions()) {
            poll.addOption(PollOption.builder()
                    .poll(poll)
                    .optionText(optionText.strip())
                    .sortOrder(sortOrder++)
                    .build());
        }
        pollRepository.save(poll);
    }

    public PollResponse getPollResponse(@NonNull Long postId, Long userId) {
        return pollRepository.findByPost_PostId(postId)
                .map(poll -> toResponse(poll, userId))
                .orElse(null);
    }

    @Transactional
    public PollResponse vote(Long userId, Long postId, Collection<Long> optionIds) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        Poll poll = pollRepository.findByPostIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateReadable(poll, user);
        validateOpen(poll);
        List<Long> selectedOptionIds = normalizeOptionIds(optionIds);
        if (!Boolean.TRUE.equals(poll.getMultipleChoiceEnabled()) && selectedOptionIds.size() > 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Map<Long, PollOption> optionsById = poll.getOptions().stream()
                .collect(Collectors.toMap(PollOption::getOptionId, Function.identity()));
        if (!optionsById.keySet().containsAll(selectedOptionIds)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        pollVoteRepository.deleteByPoll_PollIdAndUser_UserId(poll.getPollId(), userId);
        for (Long optionId : selectedOptionIds) {
            pollVoteRepository.save(PollVote.builder()
                    .poll(poll)
                    .option(optionsById.get(optionId))
                    .user(user)
                    .build());
        }
        return toResponse(poll, userId);
    }

    @Transactional
    public PollResponse deleteVote(Long userId, Long postId) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        Poll poll = pollRepository.findByPostIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateReadable(poll, user);
        validateOpen(poll);
        pollVoteRepository.deleteByPoll_PollIdAndUser_UserId(poll.getPollId(), userId);
        return toResponse(poll, userId);
    }

    private PollResponse toResponse(Poll poll, Long userId) {
        Map<Long, Long> voteCounts = pollVoteRepository.countByOption(poll.getPollId()).stream()
                .collect(Collectors.toMap(
                        PollVoteRepository.OptionVoteCountProjection::getOptionId,
                        PollVoteRepository.OptionVoteCountProjection::getVoteCount));
        Set<Long> selectedOptionIds = userId == null
                ? Set.of()
                : new HashSet<>(pollVoteRepository.findSelectedOptionIds(poll.getPollId(), userId));
        return PollResponse.from(poll, voteCounts, selectedOptionIds);
    }

    private void validatePollRequest(PollRequest request) {
        if (request.getQuestion() == null
                || request.getQuestion().strip().isBlank()
                || request.getQuestion().length() > 200
                || request.getOptions() == null || request.getOptions().size() < 2 || request.getOptions().size() > 10
                || request.getOptions().stream().anyMatch(option -> option == null
                || option.strip().isBlank()
                || option.length() > 100)
                || request.getClosesAt() != null && !request.getClosesAt().isAfter(now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<Long> normalizeOptionIds(Collection<Long> optionIds) {
        if (optionIds == null || optionIds.isEmpty() || optionIds.size() > 10
                || optionIds.stream().anyMatch(optionId -> optionId == null || optionId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<Long> normalized = optionIds.stream().distinct().toList();
        if (normalized.size() != optionIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private void validateOpen(Poll poll) {
        if (poll.getClosesAt() != null && !poll.getClosesAt().isAfter(now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void validateReadable(Poll poll, User viewer) {
        Post post = poll.getPost();
        if (Boolean.TRUE.equals(post.getIsBlinded())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        PostReadContext context = postReadContextResolver.withAdminBoardIdsForPosts(
                postReadContextResolver.resolveForResolvedUser(viewer),
                List.of(post));
        postAccessPolicy.validateReadable(
                post,
                context.viewer(),
                context.isAuthorBlocked(post),
                context.activeAdminBoardIds());
    }
}
