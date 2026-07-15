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

    void updatePresentation(EmoticonMaster master) {
        resolveSingleItem(master.getEmoticonId())
                .updatePresentation(master.getName(), master.getThumbnailUrl());
    }

    void setActive(Long emoticonId, boolean active) {
        ShopItem item = resolveSingleItem(emoticonId);
        if (active) {
            item.activate();
        } else {
            item.deactivate();
        }
    }

    void retire(Long emoticonId) {
        resolveSingleItem(emoticonId).retire();
    }

    PurchaseOffer getPurchaseOffer(Long emoticonId) {
        List<ShopItem> items = findItems(emoticonId);
        if (items.size() != 1) {
            return new PurchaseOffer(false, resolveCurrentPrice());
        }
        ShopItem item = items.get(0);
        return new PurchaseOffer(Boolean.TRUE.equals(item.getIsActive()), item.getPrice());
    }

    private ShopItem resolveSingleItem(Long emoticonId) {
        List<ShopItem> items = findItems(emoticonId);
        if (items.size() != 1) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE);
        }
        return items.get(0);
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
