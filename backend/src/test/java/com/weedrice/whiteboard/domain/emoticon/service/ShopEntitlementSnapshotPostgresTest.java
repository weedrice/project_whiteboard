package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.repository.ShopItemRepository;
import com.weedrice.whiteboard.domain.shop.service.ShopEntitlementCapabilityRegistry;
import com.weedrice.whiteboard.domain.shop.service.ShopEntitlementStartupValidator;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, ShopEntitlementStartupValidator.class, ShopEntitlementCapabilityRegistry.class,
        EmoticonShopEntitlementHandler.class, EmoticonEntitlementGrantService.class})
class ShopEntitlementSnapshotPostgresTest {
    @Autowired private ShopEntitlementStartupValidator validator;
    @Autowired private ShopItemRepository items;
    @Autowired private EmoticonMasterRepository masters;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private EmoticonShopEntitlementHandler handler;

    private TransactionTemplate writer;
    private Long itemId;
    private Long masterId;

    @BeforeEach
    void createActiveOffer() {
        writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writer.executeWithoutResult(status -> {
            masterId = masters.saveAndFlush(EmoticonMaster.builder().name("Snapshot test").build()).getEmoticonId();
            itemId = items.saveAndFlush(ShopItem.builder().itemName("Snapshot test").itemType("EMOTICON")
                    .targetId(masterId).price(100).build()).getItemId();
        });
    }

    @AfterEach
    void removeOffer() {
        writer.executeWithoutResult(status -> {
            items.deleteById(itemId);
            items.flush();
            masters.deleteById(masterId);
        });
    }

    @Test
    void hideCommittedAfterTheActiveItemQueryDoesNotFailStartup() {
        doAnswer(call -> {
            // The list was already read. Commit the creator's paired updates on a separate connection.
            writer.executeWithoutResult(status -> {
                items.findByIdForUpdate(itemId).orElseThrow().deactivate();
                masters.findByIdForUpdate(masterId).orElseThrow().deactivate();
            });
            return call.callRealMethod();
        }).when(handler).validateConfiguration(argThat(item -> itemId.equals(item.getItemId())));

        validator.afterSingletonsInstantiated();

        assertThat(items.findById(itemId).orElseThrow().getIsActive()).isFalse();
        assertThat(masters.findById(masterId).orElseThrow().getIsActive()).isEqualTo("N");
    }

    @Test
    void activeItemWithAnAlreadyHiddenTargetStillFailsStartup() {
        writer.executeWithoutResult(status -> masters.findByIdForUpdate(masterId).orElseThrow().deactivate());

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("itemId=" + itemId + ", itemType=EMOTICON, reason=ITEM_NOT_AVAILABLE");
    }
}
