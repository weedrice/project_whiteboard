package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ScrapFolderRequest {
    @Size(max = 60)
    private String name;
    private Integer sortOrder;
}
