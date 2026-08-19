package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.List;

class EmoticonShopItemLifecycleService {

    static final int DEFAULT_PRICE = 100;

    private final ShopItemRepository shopItemRepository;
    private final GlobalConfigService globalConfigService;

    EmoticonShopItemLifecycleService(ShopItemRepository shopItemRepository,
                                     GlobalConfigService globalConfigService) {
        this.shopItemRepository = shopItemRepository;
        this.globalConfigService = globalConfigService;
    }

    void createFor(EmoticonMaster master) {
        int priceSnapshot = GlobalConfigService.parseIntConfigOrDefault(
                globalConfigService.getConfigFresh(GlobalConfigService.NOBICON_PRICE_CONFIG_KEY), DEFAULT_PRICE, 0);
        shopItemRepository.save(ShopItem.builder()
                .itemName(master.getName())
                .price(priceSnapshot)
                .itemType(EmoticonShopItemTypes.EMOTICON)
                .targetId(master.getEmoticonId())
                .imageUrl(master.getThumbnailUrl())
                .build());
    }

    void updatePresentation(ShopItem item, EmoticonMaster master) {
        item.updatePresentation(master.getName(), master.getThumbnailUrl());
    }

    void setActive(ShopItem item, boolean active) {
        if (active) {
            item.activate();
        } else {
            item.deactivate();
        }
    }

    void retire(ShopItem item) {
        item.retire();
    }

    PurchaseOffer getPurchaseOffer(Long emoticonId) {
        List<ShopItem> items = findItems(emoticonId);
        if (items.size() != 1) {
            return new PurchaseOffer(false, resolveCurrentPrice());
        }
        ShopItem item = items.get(0);
        return new PurchaseOffer(item.isPurchasable(), item.getPrice());
    }

    ShopItem lockForUpdate(Long emoticonId) {
        List<ShopItem> items = shopItemRepository.findByItemTypeAndTargetIdForUpdate(
                EmoticonShopItemTypes.EMOTICON,
                emoticonId);
        if (items.isEmpty()) {
            return null;
        }
        if (items.size() != 1) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }
        return items.getFirst();
    }

    private List<ShopItem> findItems(Long emoticonId) {
        return shopItemRepository.findByItemTypeAndTargetId(EmoticonShopItemTypes.EMOTICON, emoticonId);
    }

    private int resolveCurrentPrice() {
        return GlobalConfigService.parseIntConfigOrDefault(
                globalConfigService.getConfigFresh(GlobalConfigService.NOBICON_PRICE_CONFIG_KEY),
                DEFAULT_PRICE,
                0);
    }

    record PurchaseOffer(boolean available, int price) {
    }
}
