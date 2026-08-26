package com.weedrice.whiteboard.domain.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminInquiryCloseRequest(@NotBlank @Size(max = 500) String reason) {
}
