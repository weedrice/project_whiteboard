package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ScrapFolderAssignmentRequest {

    @Positive
    private Long folderId;
}
