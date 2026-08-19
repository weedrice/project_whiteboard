package com.weedrice.whiteboard.domain.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminShopItemSaleStatusRequest(
        @NotNull Boolean saleEnabled,
        @NotBlank @Size(max = 500) String reason) {
}
