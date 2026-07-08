package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.ScrapFolder;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScrapFolderResponse {
    private Long folderId;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ScrapFolderResponse from(ScrapFolder folder) {
        return ScrapFolderResponse.builder()
                .folderId(folder.getFolderId())
                .name(folder.getName())
                .sortOrder(folder.getSortOrder())
                .createdAt(folder.getCreatedAt())
                .modifiedAt(folder.getModifiedAt())
                .build();
    }

    public static List<ScrapFolderResponse> listFrom(List<ScrapFolder> folders) {
        return folders.stream()
                .map(ScrapFolderResponse::from)
                .toList();
    }
}
