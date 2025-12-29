package com.weedrice.whiteboard.domain.tag.controller;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.tag.dto.TagResponse;
import com.weedrice.whiteboard.domain.tag.service.TagService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final PostService postService;

    @GetMapping
    public ApiResponse<TagResponse> getPopularTags() {
        return ApiResponse.success(TagResponse.from(tagService.getPopularTags()));
    }

    @GetMapping("/{tagId}/posts")
    public ApiResponse<PageResponse<PostSummary>> getPostsByTag(
            @PathVariable Long tagId,
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        return ApiResponse.success(new PageResponse<>(postService.getPostsByTag(tagId, userId, pageable)));
    }
}
