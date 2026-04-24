package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.shop.dto.PurchaseHistoryResponse;
import com.weedrice.whiteboard.domain.shop.dto.ShopItemResponse;
import com.weedrice.whiteboard.domain.shop.entity.PurchaseHistory;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.PurchaseHistoryRepository;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private final ShopItemRepository shopItemRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final ShopEntitlementCapabilityRegistry shopEntitlementCapabilityRegistry;
    private final SanctionService sanctionService;

    public ShopItemResponse getShopItems(String itemType, Pageable pageable) {
        if (itemType != null && !itemType.isEmpty() && !shopEntitlementCapabilityRegistry.supports(itemType)) {
            return emptyShopItems(pageable);
        }

        Set<String> supportedItemTypes = shopEntitlementCapabilityRegistry.getSupportedItemTypes();
        if (supportedItemTypes.isEmpty()) {
            return emptyShopItems(pageable);
        }

        Page<ShopItem> items;
        if (itemType != null && !itemType.isEmpty()) {
            items = shopItemRepository.findByIsActiveAndItemType(true, itemType, pageable);
        } else {
            items = shopItemRepository.findByIsActiveAndItemTypeIn(true, supportedItemTypes, pageable);
        }
        return ShopItemResponse.from(items);
    }

    public ShopItem getShopItem(Long itemId) {
        return shopItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));
    }

    @Transactional
    public Long purchaseItem(Long userId, Long itemId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(user);
        ShopItem item = shopItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));

        if (!item.getIsActive() || !shopEntitlementCapabilityRegistry.supports(item)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        try {
            shopEntitlementCapabilityRegistry.validateConfiguration(item);
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        pointService.spendPoint(userId, item.getPrice(), "Shop item purchase: " + item.getItemName(), item.getItemId(), "SHOP_ITEM");
        shopEntitlementCapabilityRegistry.grant(userId, item);

        PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                .user(user)
                .item(item)
                .purchasedPrice(item.getPrice())
                .build();
        return purchaseHistoryRepository.save(purchaseHistory).getPurchaseId();
    }

    public PurchaseHistoryResponse getPurchaseHistories(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return PurchaseHistoryResponse.from(purchaseHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable));
    }

    private ShopItemResponse emptyShopItems(Pageable pageable) {
        return ShopItemResponse.from(new PageImpl<>(List.of(), pageable, 0));
    }
}
