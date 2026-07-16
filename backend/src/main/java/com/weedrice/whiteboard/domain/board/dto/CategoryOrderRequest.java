package com.weedrice.whiteboard.domain.board.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CategoryOrderRequest(
        @NotEmpty
        @Size(max = 500)
        List<@NotNull @Positive Long> categoryIds) {
}
