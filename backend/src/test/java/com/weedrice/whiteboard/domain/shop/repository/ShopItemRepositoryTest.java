package com.weedrice.whiteboard.domain.shop.repository;

import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class ShopItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ShopItemRepository shopItemRepository;

    @Test
    void publicQueryReturnsOnlySourceActiveAndSaleEnabledItems() {
        ShopItem onSale = persistItem("Premium pack", 10L);
        ShopItem suspended = persistItem("Suspended pack", 11L);
        suspended.suspendSale();
        entityManager.flush();
        entityManager.clear();

        var result = shopItemRepository.findByIsActiveAndIsSaleEnabledAndItemType(
                true,
                true,
                "EMOTICON",
                PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(ShopItem::getItemId)
                .containsExactly(onSale.getItemId());
    }

    @Test
    void adminSearchFiltersSaleStatusAndUsesCaseInsensitiveNameContainment() {
        persistItem("Standard pack", 10L);
        ShopItem suspended = persistItem("Premium Pack", 11L);
        suspended.suspendSale();
        entityManager.flush();
        entityManager.clear();

        var result = shopItemRepository.searchAdminItems(
                "premium",
                "EMOTICON",
                true,
                false,
                PageRequest.of(0, 20));

        assertThat(result.getContent())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getItemId()).isEqualTo(suspended.getItemId());
                    assertThat(item.getIsSaleEnabled()).isFalse();
                });
        Object storedValue = entityManager.getEntityManager()
                .createNativeQuery("SELECT is_sale_enabled FROM shop_items WHERE item_id = :itemId")
                .setParameter("itemId", suspended.getItemId())
                .getSingleResult();
        assertThat(storedValue.toString()).isEqualTo("N");
    }

    private ShopItem persistItem(String name, long targetId) {
        ShopItem item = ShopItem.builder()
                .itemName(name)
                .description("Shop item")
                .price(100)
                .itemType("EMOTICON")
                .targetId(targetId)
                .build();
        entityManager.persist(item);
        return item;
    }
}
