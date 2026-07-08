package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PollVoteRequest {
    @NotEmpty
    private List<Long> optionIds;
}
