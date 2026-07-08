package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.Poll;
import com.weedrice.whiteboard.domain.post.entity.PollOption;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Builder
public class PollResponse {
    private Long pollId;
    private String question;
    private boolean multipleChoiceEnabled;
    private boolean anonymousEnabled;
    private LocalDateTime closesAt;
    private List<Option> options;

    public static PollResponse from(Poll poll, Map<Long, Long> voteCounts, Set<Long> selectedOptionIds) {
        return PollResponse.builder()
                .pollId(poll.getPollId())
                .question(poll.getQuestion())
                .multipleChoiceEnabled(Boolean.TRUE.equals(poll.getMultipleChoiceEnabled()))
                .anonymousEnabled(Boolean.TRUE.equals(poll.getAnonymousEnabled()))
                .closesAt(poll.getClosesAt())
                .options(poll.getOptions().stream()
                        .map(option -> Option.from(option, voteCounts.getOrDefault(option.getOptionId(), 0L),
                                selectedOptionIds.contains(option.getOptionId())))
                        .toList())
                .build();
    }

    @Getter
    @Builder
    public static class Option {
        private Long optionId;
        private String optionText;
        private int sortOrder;
        private long voteCount;
        private boolean selected;

        static Option from(PollOption option, long voteCount, boolean selected) {
            return Option.builder()
                    .optionId(option.getOptionId())
                    .optionText(option.getOptionText())
                    .sortOrder(option.getSortOrder())
                    .voteCount(voteCount)
                    .selected(selected)
                    .build();
        }
    }
}
