package com.weedrice.whiteboard.domain.shop.controller;

import com.weedrice.whiteboard.domain.shop.dto.PurchaseHistoryResponse;
import com.weedrice.whiteboard.domain.shop.dto.ShopItemResponse;
import com.weedrice.whiteboard.domain.shop.service.ShopService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @GetMapping("/items")
    public ApiResponse<ShopItemResponse> getShopItems(
            @RequestParam(required = false) String itemType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequestUtils.of(page, size);
        return ApiResponse.success(shopService.getShopItems(itemType, pageable));
    }

    @PostMapping("/items/{itemId}/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> purchaseItem(
            @PathVariable Long itemId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(shopService.purchaseItem(userId, itemId));
    }

    @GetMapping("/me/purchases")
    public ApiResponse<PurchaseHistoryResponse> getMyPurchaseHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserId Long userId) {
        Pageable pageable = PageRequestUtils.of(page, size);
        return ApiResponse.success(shopService.getPurchaseHistories(userId, pageable));
    }
}
