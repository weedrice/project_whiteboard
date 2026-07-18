package com.weedrice.whiteboard.domain.search.dto;

import com.weedrice.whiteboard.domain.board.dto.BoardSummary;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.user.dto.UserSummary;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class IntegratedSearchResponse {
    private IntegratedSearchResultGroup<PostSummary> postResults;
    private IntegratedSearchResultGroup<CommentResponse> commentResults;
    private IntegratedSearchResultGroup<UserSummary> userResults;
    private List<BoardSummary> boardResults;
    private IntegratedSearchResultGroup<BoardSummary> boardResultPage;
    private String keyword;

    public static IntegratedSearchResponse from(Page<PostSummary> postPage, Page<CommentResponse> commentPage,
            Page<UserSummary> userPage, Page<BoardSummary> boardPage, String keyword) {
        return IntegratedSearchResponse.builder()
                .postResults(IntegratedSearchResultGroup.from(postPage))
                .commentResults(IntegratedSearchResultGroup.from(commentPage))
                .userResults(IntegratedSearchResultGroup.from(userPage))
                .boardResults(boardPage.getContent())
                .boardResultPage(IntegratedSearchResultGroup.from(boardPage))
                .keyword(keyword)
                .build();
    }

    public static IntegratedSearchResponse from(Page<PostSummary> postPage, Page<CommentResponse> commentPage,
            Page<UserSummary> userPage, List<BoardSummary> boardList, String keyword) {
        return IntegratedSearchResponse.builder()
                .postResults(IntegratedSearchResultGroup.from(postPage))
                .commentResults(IntegratedSearchResultGroup.from(commentPage))
                .userResults(IntegratedSearchResultGroup.from(userPage))
                .boardResults(boardList)
                .boardResultPage(IntegratedSearchResultGroup.<BoardSummary>builder()
                        .items(boardList)
                        .totalElements(boardList.size())
                        .totalPages(boardList.isEmpty() ? 0 : 1)
                        .page(0)
                        .size(boardList.size())
                        .hasMore(false)
                        .build())
                .keyword(keyword)
                .build();
    }
}
