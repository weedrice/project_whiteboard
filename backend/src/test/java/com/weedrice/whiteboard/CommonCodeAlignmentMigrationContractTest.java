package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CommonCodeAlignmentMigrationContractTest {

    @Test
    void referenceCommonCodesMatchCurrentRuntimeValues() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V96__align_reference_common_codes.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("('VERSION_TYPE', 'MODIFY', '수정', 20, 'Y'")
                .contains("('ADMIN_ROLE', 'BOARD_ADMIN', '스페이스 관리자', 10, 'Y'")
                .contains("('ADMIN_ROLE', 'MODERATOR', '운영자', 20, 'Y'")
                .contains("('RANKING_TYPE', 'DAILY', '일간 랭킹', 10, 'Y'")
                .contains("('RANKING_TYPE', 'WEEKLY', '주간 랭킹', 20, 'Y'")
                .contains("code_value = 'UPDATE'")
                .contains("code_value IN ('MONTHLY', 'REALTIME')")
                .contains("type_code = 'ACTION_TYPE'");
    }
}
