package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonPurchaseRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EmoticonServiceConfig {

    private static final int EMOTICON_PRICE = 100;
    private static final String EMOTICON_THUMBNAIL = "EMOTICON_THUMBNAIL";
    private static final String EMOTICON_IMAGE = "EMOTICON_IMAGE";

    @Bean
    EmoticonAttachmentHelper emoticonAttachmentHelper(FileService fileService) {
        return new EmoticonAttachmentHelper(fileService);
    }

    @Bean
    EmoticonCatalogService emoticonCatalogService(EmoticonMasterRepository emoticonMasterRepository,
                                                  UserRepository userRepository) {
        return new EmoticonCatalogService(emoticonMasterRepository, userRepository, EMOTICON_PRICE);
    }

    @Bean
    EmoticonCommandService emoticonCommandService(EmoticonMasterRepository emoticonMasterRepository,
                                                   EmoticonImageRepository emoticonImageRepository,
                                                   EmoticonPurchaseRepository emoticonPurchaseRepository,
                                                   UserRepository userRepository,
                                                   EmoticonAttachmentHelper attachmentHelper) {
        return new EmoticonCommandService(
                emoticonMasterRepository,
                emoticonImageRepository,
                emoticonPurchaseRepository,
                userRepository,
                attachmentHelper,
                EMOTICON_THUMBNAIL,
                EMOTICON_IMAGE);
    }

    @Bean
    EmoticonEntitlementGrantService emoticonEntitlementGrantService(EmoticonMasterRepository emoticonMasterRepository,
                                                                    EmoticonPurchaseRepository emoticonPurchaseRepository,
                                                                    UserRepository userRepository) {
        return new EmoticonEntitlementGrantService(
                emoticonMasterRepository,
                emoticonPurchaseRepository,
                userRepository);
    }

    @Bean
    EmoticonPurchaseService emoticonPurchaseService(EmoticonEntitlementGrantService emoticonEntitlementGrantService,
                                                    PointService pointService,
                                                    SanctionService sanctionService) {
        return new EmoticonPurchaseService(
                emoticonEntitlementGrantService,
                pointService,
                sanctionService,
                EMOTICON_PRICE);
    }
}
