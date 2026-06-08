package com.weedrice.whiteboard.domain.emoticon.controller;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonCreateRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonImageAddRequest;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonPurchaseStatusResponse;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonUpdateRequest;
import com.weedrice.whiteboard.domain.emoticon.service.EmoticonService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/emoticons")
@RequiredArgsConstructor
public class EmoticonController {

    private final EmoticonService emoticonService;

    @GetMapping
    public ApiResponse<PageResponse<EmoticonMasterDto>> getEmoticons(
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.page(emoticonService.getActiveEmoticons(pageable(page, size), sortBy));
    }

    @GetMapping("/popular")
    public ApiResponse<List<EmoticonMasterDto>> getPopularEmoticons(
            @RequestParam(defaultValue = "daily") String period) {
        return ApiResponse.success(emoticonService.getPopularEmoticons(period));
    }

    @GetMapping("/search/all")
    public ApiResponse<PageResponse<EmoticonMasterDto>> searchAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String searchType,
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.page(
                emoticonService.searchAll(keyword, searchType, pageable(page, size), sortBy));
    }

    @GetMapping("/search/tag")
    public ApiResponse<PageResponse<EmoticonMasterDto>> searchByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.page(emoticonService.searchByTag(tag, pageable(page, size)));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<EmoticonMasterDto>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.page(emoticonService.searchByKeyword(keyword, pageable(page, size)));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<EmoticonMasterDto>> getMyEmoticons(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.page(emoticonService.getMyEmoticons(userId, pageable(page, size)));
    }

    @GetMapping("/{emoticonId}")
    public ApiResponse<EmoticonMasterDto> getEmoticonDetail(
            @PathVariable Long emoticonId,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(emoticonService.getEmoticonDetail(emoticonId, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmoticonMasterDto> createEmoticon(
            @CurrentUserId Long userId,
            @Valid @RequestBody EmoticonCreateRequest request) {
        return ApiResponse.success(emoticonService.createEmoticon(userId, request));
    }

    @PutMapping("/{emoticonId}")
    public ApiResponse<EmoticonMasterDto> updateEmoticon(
            @CurrentUserId Long userId,
            @PathVariable Long emoticonId,
            @Valid @RequestBody EmoticonUpdateRequest request) {
        return ApiResponse.success(emoticonService.updateEmoticon(userId, emoticonId, request));
    }

    @PatchMapping("/{emoticonId}/visibility")
    public ApiResponse<EmoticonMasterDto> toggleVisibility(
            @CurrentUserId Long userId,
            @PathVariable Long emoticonId) {
        return ApiResponse.success(emoticonService.toggleVisibility(userId, emoticonId));
    }

    @DeleteMapping("/{emoticonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteEmoticon(
            @CurrentUserId Long userId,
            @PathVariable Long emoticonId) {
        emoticonService.deleteEmoticon(userId, emoticonId);
        return ApiResponses.ok();
    }

    @PostMapping("/{emoticonId}/images")
    public ApiResponse<EmoticonMasterDto> addImage(
            @CurrentUserId Long userId,
            @PathVariable Long emoticonId,
            @Valid @RequestBody EmoticonImageAddRequest request) {
        return ApiResponse.success(emoticonService.addImage(
                userId,
                emoticonId,
                request.getFileId()));
    }

    @DeleteMapping("/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteImage(
            @CurrentUserId Long userId,
            @PathVariable Long imageId) {
        emoticonService.deleteImage(userId, imageId);
        return ApiResponses.ok();
    }

    @PostMapping("/{emoticonId}/purchase")
    public ApiResponse<EmoticonMasterDto> purchaseEmoticon(
            @CurrentUserId Long userId,
            @PathVariable Long emoticonId) {
        return ApiResponse.success(emoticonService.purchaseEmoticon(userId, emoticonId));
    }

    @GetMapping("/purchased")
    public ApiResponse<PageResponse<EmoticonMasterDto>> getPurchasedEmoticons(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.page(emoticonService.getPurchasedEmoticons(userId, pageable(page, size)));
    }

    @GetMapping("/{emoticonId}/purchased")
    public ApiResponse<EmoticonPurchaseStatusResponse> hasPurchased(
            @CurrentUserId(required = false) Long userId,
            @PathVariable Long emoticonId) {
        return ApiResponse.success(emoticonService.getPurchaseStatus(userId, emoticonId));
    }

    private Pageable pageable(int page, int size) {
        return PageRequestUtils.of(page, size);
    }
}
