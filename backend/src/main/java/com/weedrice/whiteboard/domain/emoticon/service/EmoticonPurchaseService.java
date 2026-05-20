package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.shop.service.ShopService;

class EmoticonPurchaseService {

    private static final String EMOTICON_ITEM_TYPE = "EMOTICON";

    private final ShopService shopService;
    private final EmoticonCatalogService catalogService;

    EmoticonPurchaseService(ShopService shopService,
                            EmoticonCatalogService catalogService) {
        this.shopService = shopService;
        this.catalogService = catalogService;
    }

    EmoticonMasterDto purchaseEmoticon(Long userId, Long emoticonId) {
        shopService.purchaseActiveItemByTarget(userId, EMOTICON_ITEM_TYPE, emoticonId);

        return catalogService.getEmoticonDetail(emoticonId, userId);
    }

    int getEmoticonPrice(Long emoticonId) {
        return shopService.resolveSingleActiveItemByTarget(EMOTICON_ITEM_TYPE, emoticonId).getPrice();
    }
}
