package com.weedrice.whiteboard.domain.common.repository;

import com.weedrice.whiteboard.domain.common.entity.CommonCode;
import com.weedrice.whiteboard.domain.common.entity.CommonCodeDetail;
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
class CommonCodeDetailRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CommonCodeDetailRepository commonCodeDetailRepository;

    @Test
    @DisplayName("active details are ordered by sortOrder then codeValue")
    void findByCommonCodeAndIsActiveOrderBySortOrderAscCodeValueAsc_ordersBySortOrderAndCodeValue() {
        CommonCode commonCode = CommonCode.builder()
                .typeCode("TEST_TYPE")
                .typeName("Test Type")
                .build();
        entityManager.persist(commonCode);

        entityManager.persist(detail(commonCode, "B", "B Name", 1, true));
        entityManager.persist(detail(commonCode, "A", "A Name", 1, true));
        entityManager.persist(detail(commonCode, "C", "C Name", 0, true));
        entityManager.persist(detail(commonCode, "D", "D Name", 0, false));
        entityManager.flush();
        entityManager.clear();

        CommonCode persistedCommonCode = entityManager.find(CommonCode.class, "TEST_TYPE");
        var result = commonCodeDetailRepository
                .findByCommonCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(persistedCommonCode, true);

        assertThat(result)
                .extracting(CommonCodeDetail::getCodeValue)
                .containsExactly("C", "A", "B");
    }

    @Test
    @DisplayName("typeCode direct active details are ordered by sortOrder then codeValue")
    void findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAscCodeValueAsc_ordersBySortOrderAndCodeValue() {
        CommonCode commonCode = CommonCode.builder()
                .typeCode("DIRECT_TYPE")
                .typeName("Direct Type")
                .build();
        CommonCode otherCode = CommonCode.builder()
                .typeCode("OTHER_TYPE")
                .typeName("Other Type")
                .build();
        entityManager.persist(commonCode);
        entityManager.persist(otherCode);

        entityManager.persist(detail(commonCode, "B", "B Name", 1, true));
        entityManager.persist(detail(commonCode, "A", "A Name", 1, true));
        entityManager.persist(detail(commonCode, "C", "C Name", 0, true));
        entityManager.persist(detail(commonCode, "D", "D Name", 0, false));
        entityManager.persist(detail(otherCode, "E", "E Name", 0, true));
        entityManager.flush();
        entityManager.clear();

        var result = commonCodeDetailRepository
                .findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAscCodeValueAsc("DIRECT_TYPE", true);

        assertThat(result)
                .extracting(CommonCodeDetail::getCodeValue)
                .containsExactly("C", "A", "B");
    }

    @Test
    @DisplayName("admin details include inactive rows and preserve sort order")
    void findByCommonCode_TypeCodeOrderBySortOrderAscCodeValueAsc_includesInactiveRows() {
        CommonCode commonCode = CommonCode.builder()
                .typeCode("ADMIN_TYPE")
                .typeName("Admin Type")
                .build();
        entityManager.persist(commonCode);
        entityManager.persist(detail(commonCode, "ACTIVE", "Active", 20, true));
        entityManager.persist(detail(commonCode, "INACTIVE", "Inactive", 10, false));
        entityManager.flush();
        entityManager.clear();

        var result = commonCodeDetailRepository
                .findByCommonCode_TypeCodeOrderBySortOrderAscCodeValueAsc("ADMIN_TYPE");

        assertThat(result).extracting(CommonCodeDetail::getCodeValue)
                .containsExactly("INACTIVE", "ACTIVE");
    }

    private CommonCodeDetail detail(CommonCode commonCode, String codeValue, String codeName,
            Integer sortOrder, Boolean isActive) {
        return CommonCodeDetail.builder()
                .commonCode(commonCode)
                .codeValue(codeValue)
                .codeName(codeName)
                .sortOrder(sortOrder)
                .isActive(isActive)
                .build();
    }
}
