package com.weedrice.whiteboard.domain.search.dto;

import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class IntegratedSearchResponse {
    private Page<PostSummary> posts;
    private Page<MyCommentResponse> comments;
    private Page<UserProfileResponse> users;

    public static IntegratedSearchResponse from(Page<PostSummary> postPage, Page<MyCommentResponse> commentPage, Page<UserProfileResponse> userPage, String keyword) {
        return IntegratedSearchResponse.builder()
                .posts(postPage)
                .comments(commentPage)
                .users(userPage)
                .build();
    }
}
