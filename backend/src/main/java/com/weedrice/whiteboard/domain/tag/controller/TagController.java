package com.weedrice.whiteboard.domain.tag.controller;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.dto.TagResponse;
import com.weedrice.whiteboard.domain.tag.service.TagService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

    @GetMapping("/{tagKey}/posts")
    public ApiResponse<PageResponse<PostSummary>> getPostsByTag(
            @PathVariable String tagKey,
            @PageableDefault(size = 20) Pageable pageable,
            @CurrentUserId(required = false) Long userId) {
        if (tagKey.chars().allMatch(Character::isDigit)) {
            return ApiResponses.page(postService.getPostsByTag(Long.parseLong(tagKey), userId, pageable));
        }
        Tag tag = tagService.getByName(tagKey);
        return ApiResponses.page(postService.getPostsByTag(tag.getTagId(), userId, pageable));
    }
}
