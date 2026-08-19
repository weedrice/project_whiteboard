package com.weedrice.whiteboard.domain.shop;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ShopSaleAvailabilityMigrationContractTest {

    @Test
    void saleAvailabilityColumnDefaultsExistingAndLegacyWritesToEnabled() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V89__add_shop_item_sale_enabled.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("is_sale_enabled VARCHAR(1) NOT NULL DEFAULT 'Y'")
                .contains("CHECK (is_sale_enabled IN ('Y', 'N'))")
                .contains("SET is_sale_enabled = 'N'")
                .contains("WHERE target_id IS NULL");
    }

    @Test
    void saleAvailabilityIndexIsAnOnlineNonTransactionalMigration() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V90__index_shop_item_sale_availability.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String configuration = new ClassPathResource(
                "db/migration/V90__index_shop_item_sale_availability.sql.conf")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("-- noviis:online-index idx_shop_items_sale_availability")
                .contains("CREATE INDEX CONCURRENTLY idx_shop_items_sale_availability")
                .contains("(is_active, is_sale_enabled, item_type, item_id)");
        assertThat(configuration.trim()).isEqualTo("executeInTransaction=false");
    }
}
