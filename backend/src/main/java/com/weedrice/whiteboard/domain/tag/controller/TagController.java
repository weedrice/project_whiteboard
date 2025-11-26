package com.weedrice.whiteboard.domain.tag.controller;

import com.weedrice.whiteboard.domain.post.dto.PostListResponse;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.tag.dto.TagResponse;
import com.weedrice.whiteboard.domain.tag.service.TagService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
    public ApiResponse<PostListResponse> getPostsByTag(@PathVariable Long tagId, Pageable pageable) {
        // TODO: 태그별 게시글 조회 로직 구현 필요
        // 현재는 PostService에 해당 기능이 없으므로, 임시로 비워둠
        return ApiResponse.success(null);
    }
}
