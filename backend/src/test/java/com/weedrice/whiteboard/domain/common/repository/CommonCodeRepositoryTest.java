package com.weedrice.whiteboard.domain.common.repository;

import com.weedrice.whiteboard.domain.common.entity.CommonCode;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class CommonCodeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Test
    @DisplayName("common codes are ordered by typeCode")
    void findAllByOrderByTypeCodeAsc_ordersByTypeCode() {
        entityManager.persist(commonCode("TYPE_C", "Type C"));
        entityManager.persist(commonCode("TYPE_A", "Type A"));
        entityManager.persist(commonCode("TYPE_B", "Type B"));
        entityManager.flush();
        entityManager.clear();

        var result = commonCodeRepository.findAllByOrderByTypeCodeAsc();

        assertThat(result)
                .extracting(CommonCode::getTypeCode)
                .containsExactly("TYPE_A", "TYPE_B", "TYPE_C");
    }

    private CommonCode commonCode(String typeCode, String typeName) {
        return CommonCode.builder()
                .typeCode(typeCode)
                .typeName(typeName)
                .build();
    }
}
