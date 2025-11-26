package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class DraftListResponse {
    private List<DraftSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class DraftSummary {
        private Long draftId;
        private String title;
        private Long boardId;
        private String boardName;
        private LocalDateTime modifiedAt;
    }

    public static DraftListResponse from(Page<DraftPost> draftPage) {
        List<DraftSummary> content = draftPage.getContent().stream()
                .map(draft -> DraftSummary.builder()
                        .draftId(draft.getDraftId())
                        .title(draft.getTitle())
                        .boardId(draft.getBoard().getBoardId())
                        .boardName(draft.getBoard().getBoardName())
                        .modifiedAt(draft.getModifiedAt())
                        .build())
                .collect(Collectors.toList());

        return DraftListResponse.builder()
                .content(content)
                .page(draftPage.getNumber())
                .size(draftPage.getSize())
                .totalElements(draftPage.getTotalElements())
                .totalPages(draftPage.getTotalPages())
                .hasNext(draftPage.hasNext())
                .hasPrevious(draftPage.hasPrevious())
                .build();
    }
}
