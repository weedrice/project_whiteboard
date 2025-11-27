package com.weedrice.whiteboard.domain.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MessageCreateRequest {
    @NotNull
    private Long receiverId;

    @NotBlank
    @Size(max = 5000)
    private String content;
}
