package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.board.dto.BoardSummary;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IntegratedSearchAssembler {

    public IntegratedSearchResponse assemble(Page<PostSummary> posts, Page<CommentResponse> comments,
            Page<UserSummary> users, List<BoardSummary> boards, String keyword) {
        return IntegratedSearchResponse.from(posts, comments, users, boards, keyword);
    }
}
