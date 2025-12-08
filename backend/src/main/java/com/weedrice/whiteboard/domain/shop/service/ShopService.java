package com.weedrice.whiteboard.domain.shop.service;

import com.weedrice.whiteboard.domain.point.service.PointService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private final ShopItemRepository shopItemRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final UserRepository userRepository;
    private final PointService pointService;

    public Page<ShopItem> getShopItems(String itemType, Pageable pageable) {
        if (itemType != null && !itemType.isEmpty()) {
            return shopItemRepository.findByIsActiveAndItemType(true, itemType, pageable);
        }
        return shopItemRepository.findByIsActive(true, pageable);
    }

    public ShopItem getShopItem(Long itemId) {
        return shopItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));
    }

    @Transactional
    public PurchaseHistory purchaseItem(Long userId, Long itemId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        ShopItem item = shopItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));

        if (!item.getIsActive()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }

        pointService.forceSubtractPoint(userId, item.getPrice(), item.getItemName() + " 구매", item.getItemId(), "SHOP_ITEM");

        PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                .user(user)
                .item(item)
                .purchasedPrice(item.getPrice())
                .build();
        return purchaseHistoryRepository.save(purchaseHistory);
    }

    public Page<PurchaseHistory> getPurchaseHistories(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return purchaseHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }
}
