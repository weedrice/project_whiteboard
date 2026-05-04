package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.shop.service.ShopService;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                                                  UserRepository userRepository) {
        return new EmoticonCatalogService(emoticonMasterRepository, userRepository);
    }

    @Bean
    EmoticonCommandService emoticonCommandService(EmoticonMasterRepository emoticonMasterRepository,
                                                   EmoticonImageRepository emoticonImageRepository,
                                                   EmoticonPurchaseRepository emoticonPurchaseRepository,
                                                   UserWritableResolver userWritableResolver,
                                                   EmoticonAttachmentHelper attachmentHelper) {
        return new EmoticonCommandService(
                emoticonMasterRepository,
                emoticonImageRepository,
                emoticonPurchaseRepository,
                userWritableResolver,
                attachmentHelper,
                EMOTICON_THUMBNAIL,
                EMOTICON_IMAGE);
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
    EmoticonPurchaseService emoticonPurchaseService(ShopItemRepository shopItemRepository,
                                                    ShopService shopService,
                                                    EmoticonCatalogService catalogService) {
        return new EmoticonPurchaseService(
                shopItemRepository,
                shopService,
                catalogService);
    }
}
