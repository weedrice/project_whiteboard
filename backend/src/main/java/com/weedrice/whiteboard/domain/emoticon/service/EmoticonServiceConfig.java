package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.shop.service.ShopService;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
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
                                                    EmoticonImageRepository emoticonImageRepository,
                                                    UserWritableResolver userWritableResolver,
                                                    EmoticonAttachmentHelper attachmentHelper,
                                                    EmoticonDeletePolicy deletePolicy) {
        return new EmoticonCommandService(
                emoticonMasterRepository,
                emoticonImageRepository,
                userWritableResolver,
                attachmentHelper,
                deletePolicy,
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
    EmoticonPurchaseService emoticonPurchaseService(ShopService shopService,
                                                    EmoticonCatalogService catalogService) {
        return new EmoticonPurchaseService(
                shopService,
                catalogService);
    }
}
