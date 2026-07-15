package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.shop.service.ShopService;

class EmoticonPurchaseService {

    private final ShopService shopService;
    private final EmoticonCatalogService catalogService;
    private final EmoticonShopItemLifecycleService shopItemLifecycleService;

    EmoticonPurchaseService(ShopService shopService,
                            EmoticonCatalogService catalogService,
                            EmoticonShopItemLifecycleService shopItemLifecycleService) {
        this.shopService = shopService;
        this.catalogService = catalogService;
        this.shopItemLifecycleService = shopItemLifecycleService;
    }

    EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        shopService.purchaseActiveItemByTarget(userId, EmoticonShopItemTypes.EMOTICON, emoticonId);

        return catalogService.getEmoticonDetail(emoticonId, userId);
    }

    int getEmoticonPrice(Long emoticonId) {
        return shopService.resolveSingleActiveItemByTarget(EmoticonShopItemTypes.EMOTICON, emoticonId).getPrice();
    }

    EmoticonShopItemLifecycleService.PurchaseOffer getPurchaseOffer(Long emoticonId) {
        return shopItemLifecycleService.getPurchaseOffer(emoticonId);
    }
}
