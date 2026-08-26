package com.weedrice.whiteboard.domain.inquiry.dto;

import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InquiryCreateRequest(
        @NotNull InquiryCategory category,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10_000) String content,
        @Size(max = 5) List<@NotNull Long> fileIds) {
}
