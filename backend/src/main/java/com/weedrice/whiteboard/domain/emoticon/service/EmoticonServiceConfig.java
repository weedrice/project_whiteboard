package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.shop.service.ShopService;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class EmoticonServiceConfig {

    private static final String EMOTICON_THUMBNAIL = "EMOTICON_THUMBNAIL";
    private static final String EMOTICON_IMAGE = "EMOTICON_IMAGE";

    @Bean
    EmoticonAttachmentHelper emoticonAttachmentHelper(FileService fileService) {
        return new EmoticonAttachmentHelper(fileService);
    }

    @Bean
    EmoticonCatalogService emoticonCatalogService(EmoticonMasterRepository emoticonMasterRepository,
                                                  UserRepository userRepository,
                                                  Clock clock) {
        return new EmoticonCatalogService(emoticonMasterRepository, userRepository, clock);
    }

    @Bean
    EmoticonDeletePolicy emoticonDeletePolicy(EmoticonPurchaseRepository emoticonPurchaseRepository) {
        return new EmoticonDeletePolicy(emoticonPurchaseRepository);
    }

    @Bean
    EmoticonCommandService emoticonCommandService(EmoticonMasterRepository emoticonMasterRepository,
                                                    UserWritableResolver userWritableResolver,
                                                    EmoticonAttachmentHelper attachmentHelper,
                                                    EmoticonDeletePolicy deletePolicy,
                                                    EmoticonImageLimitPolicy imageLimitPolicy,
                                                    EmoticonShopItemLifecycleService shopItemLifecycleService) {
        return new EmoticonCommandService(
                emoticonMasterRepository,
                userWritableResolver,
                attachmentHelper,
                deletePolicy,
                imageLimitPolicy,
                shopItemLifecycleService,
                EMOTICON_THUMBNAIL,
                EMOTICON_IMAGE);
    }

    @Bean
    EmoticonShopItemLifecycleService emoticonShopItemLifecycleService(
            ShopItemRepository shopItemRepository,
            GlobalConfigService globalConfigService) {
        return new EmoticonShopItemLifecycleService(shopItemRepository, globalConfigService);
    }

    @Bean
    EmoticonEntitlementGrantService emoticonEntitlementGrantService(EmoticonMasterRepository emoticonMasterRepository,
                                                                    EmoticonPurchaseRepository emoticonPurchaseRepository,
                                                                    UserRepository userRepository,
                                                                    EntityManager entityManager) {
        return new EmoticonEntitlementGrantService(
                emoticonMasterRepository,
                emoticonPurchaseRepository,
                userRepository,
                entityManager);
    }

    @Bean
    EmoticonPurchaseService emoticonPurchaseService(ShopService shopService,
                                                    EmoticonCatalogService catalogService,
                                                    EmoticonShopItemLifecycleService shopItemLifecycleService) {
        return new EmoticonPurchaseService(
                shopService,
                catalogService,
                shopItemLifecycleService);
    }
}
