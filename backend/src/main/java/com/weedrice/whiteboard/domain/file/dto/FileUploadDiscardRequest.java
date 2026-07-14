package com.weedrice.whiteboard.domain.file.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FileUploadDiscardRequest(
        @NotEmpty
        @Size(max = 101)
        List<Long> fileIds) {
}
