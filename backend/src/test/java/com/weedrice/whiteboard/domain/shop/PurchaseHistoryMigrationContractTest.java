package com.weedrice.whiteboard.domain.shop;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseHistoryMigrationContractTest {

    @Test
    void expandMigrationOnlyAddsNullablePurchasePresentationSnapshots() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V82__snapshot_purchase_history_item_presentation.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("item_name_snapshot VARCHAR(100)")
                .contains("item_type_snapshot VARCHAR(50)")
                .contains("image_url_snapshot VARCHAR(500)")
                .doesNotContain("UPDATE purchase_history")
                .doesNotContain("FROM shop_items")
                .doesNotContain("SET NOT NULL");
    }
}
