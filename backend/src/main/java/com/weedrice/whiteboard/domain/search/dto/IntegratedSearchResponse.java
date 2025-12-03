package com.weedrice.whiteboard.domain.search.dto;

import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.user.dto.UserSummary;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class IntegratedSearchResponse {
    private Page<PostSummary> posts;
    private Page<CommentResponse> comments;
    private Page<UserSummary> users;
    private String keyword; // Add keyword field

    public static IntegratedSearchResponse from(Page<PostSummary> postPage, Page<CommentResponse> commentPage, Page<UserSummary> userPage, String keyword) {
        return IntegratedSearchResponse.builder()
                .posts(postPage)
                .comments(commentPage)
                .users(userPage)
                .keyword(keyword) // Set keyword
                .build();
    }
}
