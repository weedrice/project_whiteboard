package com.weedrice.whiteboard.domain.shop.dto;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class ShopItemResponse {
    private List<ShopItemSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class ShopItemSummary {
        private Long itemId;
        private String itemName;
        private String description;
        private int price;
        private String itemType;
    }

    public static ShopItemResponse from(Page<ShopItem> itemPage) {
        List<ShopItemSummary> content = itemPage.getContent().stream()
                .map(item -> ShopItemSummary.builder()
                        .itemId(item.getItemId())
                        .itemName(item.getItemName())
                        .description(item.getDescription())
                        .price(item.getPrice())
                        .itemType(item.getItemType())
                        .build())
                .collect(Collectors.toList());

        return ShopItemResponse.builder()
                .content(content)
                .page(itemPage.getNumber())
                .size(itemPage.getSize())
                .totalElements(itemPage.getTotalElements())
                .totalPages(itemPage.getTotalPages())
                .hasNext(itemPage.hasNext())
                .hasPrevious(itemPage.hasPrevious())
                .build();
    }
}
