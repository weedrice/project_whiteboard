package com.weedrice.whiteboard.domain.shop.dto;

import com.weedrice.whiteboard.domain.shop.entity.PurchaseHistory;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PurchaseHistoryResponse {
    private List<PurchaseSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class PurchaseSummary {
        private Long purchaseId;
        private ItemInfo item;
        private int price;
        private LocalDateTime purchasedAt;
    }

    @Getter
    @Builder
    public static class ItemInfo {
        private Long itemId;
        private String itemName;
        private String itemType;
        private String imageUrl;
    }

    public static PurchaseHistoryResponse from(Page<PurchaseHistory> historyPage) {
        List<PurchaseSummary> content = historyPage.getContent().stream()
                .map(history -> PurchaseSummary.builder()
                        .purchaseId(history.getPurchaseId())
                        .item(ItemInfo.builder()
                                .itemId(history.getItem().getItemId())
                                .itemName(history.getPurchasedItemName())
                                .itemType(history.getPurchasedItemType())
                                .imageUrl(history.getPurchasedImageUrl())
                                .build())
                        .price(history.getPurchasedPrice())
                        .purchasedAt(history.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PurchaseHistoryResponse.builder()
                .content(content)
                .page(historyPage.getNumber())
                .size(historyPage.getSize())
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .hasNext(historyPage.hasNext())
                .hasPrevious(historyPage.hasPrevious())
                .build();
    }
}
