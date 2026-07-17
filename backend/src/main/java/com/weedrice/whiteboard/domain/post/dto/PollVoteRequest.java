package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PollVoteRequest {
    @NotEmpty
    @Size(max = 10)
    private List<@NotNull @Positive Long> optionIds;
}
