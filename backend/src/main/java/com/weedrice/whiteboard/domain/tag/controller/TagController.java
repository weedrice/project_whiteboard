package com.weedrice.whiteboard.domain.tag.controller;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostListReadService;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.domain.tag.dto.TagResponse;
import com.weedrice.whiteboard.domain.tag.dto.TagSuggestionRequest;
import com.weedrice.whiteboard.domain.tag.dto.TagSuggestionResponse;
import com.weedrice.whiteboard.domain.tag.service.TagService;
import com.weedrice.whiteboard.domain.tag.service.TagSuggestionService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final TagSuggestionService tagSuggestionService;
    private final PostListReadService postListReadService;

    @GetMapping
    public ApiResponse<TagResponse> getPopularTags() {
        return ApiResponse.success(TagResponse.fromPublicCounts(tagService.getPopularTags()));
    }

    @PostMapping("/suggestions")
    public ApiResponse<TagSuggestionResponse> suggestTags(
            @Valid @RequestBody TagSuggestionRequest request,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(tagSuggestionService.suggest(request, userId));
    }

    @GetMapping("/{tagKey}/posts")
    public ApiResponse<PageResponse<PostSummary>> getPostsByTag(
            @PathVariable String tagKey,
            @PageableDefault(size = 20) Pageable pageable,
            @CurrentUserId(required = false) Long userId) {
        Tag tag = tagKey.length() <= TagConstraints.MAX_TAG_NAME_LENGTH
                ? tagService.findByName(tagKey).orElse(null)
                : null;
        if (tag != null) {
            return ApiResponses.page(postListReadService.getPostsByTag(tag.getTagId(), userId, pageable));
        }
        if (!tagKey.isEmpty() && tagKey.chars().allMatch(Character::isDigit)) {
            try {
                return ApiResponses.page(postListReadService.getPostsByTag(Long.parseLong(tagKey), userId, pageable));
            } catch (NumberFormatException exception) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }
}
