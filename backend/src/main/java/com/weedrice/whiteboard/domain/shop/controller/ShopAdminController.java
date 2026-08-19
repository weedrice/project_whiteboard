package com.weedrice.whiteboard.domain.shop.controller;

import com.weedrice.whiteboard.domain.shop.dto.AdminShopItemResponse;
import com.weedrice.whiteboard.domain.shop.dto.AdminShopItemSaleStatusRequest;
import com.weedrice.whiteboard.domain.shop.service.AdminShopService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shop/items")
@RequiredArgsConstructor
public class ShopAdminController {

    private final AdminShopService adminShopService;

    @GetMapping
    public ApiResponse<PageResponse<AdminShopItemResponse>> getItems(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean isSaleEnabled,
            @PageableDefault(size = 20, sort = "itemId", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @CurrentUserId Long actorUserId) {
        return ApiResponses.page(adminShopService.getItems(
                actorUserId,
                q,
                itemType,
                isActive,
                isSaleEnabled,
                pageable));
    }

    @PutMapping("/{itemId}/sale-status")
    public ApiResponse<AdminShopItemResponse> updateSaleStatus(
            @PathVariable Long itemId,
            @Valid @RequestBody AdminShopItemSaleStatusRequest request,
            @Parameter(hidden = true) @CurrentUserId Long actorUserId) {
        return ApiResponse.success(adminShopService.updateSaleStatus(
                actorUserId,
                itemId,
                request.saleEnabled(),
                request.reason()));
    }
}
