package com.weedrice.whiteboard.domain.user.port;

import com.weedrice.whiteboard.domain.user.dto.AdminUserCommentResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserPostResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Consumer-owned boundary for post/comment activity exposed by the user domain. */
public interface UserActivityPort {

    PublicActivityCounts getPublicActivityCounts(Long userId);

    Page<AdminUserPostResponse> getPostsForAdmin(Long userId, Pageable pageable);

    Page<AdminUserCommentResponse> getCommentsForAdmin(Long userId, Pageable pageable);

    record PublicActivityCounts(long postCount, long commentCount) {
    }
}
