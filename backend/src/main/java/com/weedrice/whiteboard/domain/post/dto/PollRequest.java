package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PollRequest {
    @NotBlank
    @Size(max = 200)
    private String question;

    @Size(min = 2, max = 10)
    private List<@NotBlank @Size(max = 100) String> options;

    private Boolean multipleChoiceEnabled;
    private Boolean anonymousEnabled;
    private LocalDateTime closesAt;
}
