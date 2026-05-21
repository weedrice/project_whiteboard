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
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private static final int DEFAULT_SHOP_ITEM_PAGE_SIZE = 20;
    private static final Sort DEFAULT_SHOP_ITEM_SORT = Sort.by(Sort.Order.asc("itemId"));
    private static final Set<String> ALLOWED_SHOP_ITEM_SORT_PROPERTIES = Set.of("itemId");
    private static final int DEFAULT_PURCHASE_HISTORY_PAGE_SIZE = 20;
    private static final Sort DEFAULT_PURCHASE_HISTORY_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("purchaseId"));

    private final ShopItemRepository shopItemRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final ShopEntitlementCapabilityRegistry shopEntitlementCapabilityRegistry;
    private final SanctionService sanctionService;

    public ShopItemResponse getShopItems(String itemType, Pageable pageable) {
        Pageable effectivePageable = normalizeShopItemPageable(pageable);
        Set<String> supportedItemTypes = shopEntitlementCapabilityRegistry.getSupportedItemTypes();
        if (supportedItemTypes.isEmpty()) {
            return emptyShopItems(effectivePageable);
        }

        if (itemType != null && !itemType.isEmpty() && !shopEntitlementCapabilityRegistry.supports(itemType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Page<ShopItem> items;
        if (itemType != null && !itemType.isEmpty()) {
            items = shopItemRepository.findByIsActiveAndItemType(true, itemType, effectivePageable);
        } else {
            items = shopItemRepository.findByIsActiveAndItemTypeIn(true, supportedItemTypes, effectivePageable);
        }
        return ShopItemResponse.from(items);
    }

    public ShopItem getShopItem(Long itemId) {
        return shopItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));
    }

    @Transactional
    public Long purchaseItem(Long userId, Long itemId) {
        User user = resolvePurchasingUser(userId);
        ShopItem item = shopItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));
        return purchaseItem(user, item);
    }

    @Transactional
    public Long purchaseActiveItemByTarget(Long userId, String itemType, Long targetId) {
        ShopItem item = resolveSingleActiveItemByTarget(itemType, targetId);
        User user = resolvePurchasingUser(userId);
        return purchaseItem(user, item);
    }

    public ShopItem resolveSingleActiveItemByTarget(String itemType, Long targetId) {
        List<ShopItem> items = shopItemRepository.findTop2ByIsActiveAndItemTypeAndTargetId(
                true,
                itemType,
                targetId);
        if (items.size() != 1) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }
        return items.get(0);
    }

    private User resolvePurchasingUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(user);
        return user;
    }

    private Long purchaseItem(User user, ShopItem item) {
        if (!item.getIsActive() || !shopEntitlementCapabilityRegistry.supports(item)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        ShopEntitlementCapabilityRegistry.PreparedPurchase preparedPurchase = preparePurchase(user, item);
        if (item.getPrice() > 0) {
            pointService.spendPointForPrevalidatedUser(
                    user,
                    item.getPrice(),
                    "Shop item purchase: " + item.getItemName(),
                    item.getItemId(),
                    "SHOP_ITEM");
        }
        shopEntitlementCapabilityRegistry.grant(preparedPurchase);

        PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                .user(user)
                .item(item)
                .purchasedPrice(item.getPrice())
                .build();
        return purchaseHistoryRepository.save(purchaseHistory).getPurchaseId();
    }

    private ShopEntitlementCapabilityRegistry.PreparedPurchase preparePurchase(User user, ShopItem item) {
        if (shopEntitlementCapabilityRegistry.supportsValidatedPurchasePreparation(item)) {
            try {
                return shopEntitlementCapabilityRegistry.prepareValidatedPurchase(
                        user,
                        item,
                        () -> validatePurchasePrice(item));
            } catch (IllegalStateException ex) {
                throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
            }
        }

        try {
            shopEntitlementCapabilityRegistry.validateConfiguration(item);
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }
        validatePurchasePrice(item);
        return shopEntitlementCapabilityRegistry.preparePurchase(user, item);
    }

    private void validatePurchasePrice(ShopItem item) {
        if (item.getPrice() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    public PurchaseHistoryResponse getPurchaseHistories(Long userId, Pageable pageable) {
        Pageable effectivePageable = normalizePurchaseHistoryPageable(pageable);
        Page<PurchaseHistory> histories = purchaseHistoryRepository.findByUser_UserIdOrderByCreatedAtDescPurchaseIdDesc(
                userId,
                effectivePageable);
        if (histories.isEmpty() && !userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return PurchaseHistoryResponse.from(histories);
    }

    private ShopItemResponse emptyShopItems(Pageable pageable) {
        return ShopItemResponse.from(new PageImpl<>(List.of(), pageable, 0));
    }

    private Pageable normalizeShopItemPageable(Pageable pageable) {
        return PageRequestUtils.of(
                pageable,
                DEFAULT_SHOP_ITEM_PAGE_SIZE,
                DEFAULT_SHOP_ITEM_SORT,
                ALLOWED_SHOP_ITEM_SORT_PROPERTIES);
    }

    private Pageable normalizePurchaseHistoryPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequestUtils.of(0, DEFAULT_PURCHASE_HISTORY_PAGE_SIZE, DEFAULT_PURCHASE_HISTORY_SORT);
        }
        return PageRequestUtils.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_PURCHASE_HISTORY_SORT);
    }
}
