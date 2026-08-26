package com.weedrice.whiteboard.domain.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InquiryMessageCreateRequest(
        @NotBlank @Size(max = 10_000) String content,
        @Size(max = 5) List<@NotNull Long> fileIds) {
}
