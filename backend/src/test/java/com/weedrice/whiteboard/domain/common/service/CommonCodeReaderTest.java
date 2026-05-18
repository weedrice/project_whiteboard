package com.weedrice.whiteboard.domain.common.service;

import com.weedrice.whiteboard.domain.common.entity.CommonCode;
import com.weedrice.whiteboard.domain.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeDetailRepository;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonCodeReaderTest {

    @Mock
    private CommonCodeRepository commonCodeRepository;

    @Mock
    private CommonCodeDetailRepository commonCodeDetailRepository;

    @InjectMocks
    private CommonCodeReader commonCodeReader;

    private CommonCode commonCode;
    private CommonCodeDetail commonCodeDetail;

    @BeforeEach
    void setUp() {
        commonCode = CommonCode.builder()
                .typeCode("TEST_TYPE")
                .typeName("Test Type")
                .build();
        commonCodeDetail = CommonCodeDetail.builder()
                .commonCode(commonCode)
                .codeValue("TEST_VALUE")
                .codeName("Test Value")
                .sortOrder(1)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("공통 코드가 있으면 active 상세 목록을 조회한다")
    void findActiveDetails_success() {
        when(commonCodeRepository.findById("TEST_TYPE")).thenReturn(Optional.of(commonCode));
        when(commonCodeDetailRepository.findByCommonCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(
                commonCode, true)).thenReturn(List.of(commonCodeDetail));

        List<CommonCodeDetail> result = commonCodeReader.findActiveDetails("TEST_TYPE");

        assertThat(result).containsExactly(commonCodeDetail);
        verify(commonCodeRepository).findById("TEST_TYPE");
        verify(commonCodeDetailRepository).findByCommonCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(
                commonCode, true);
    }

    @Test
    @DisplayName("공통 코드가 있으면 상세가 없어도 빈 목록을 반환한다")
    void findActiveDetails_existingTypeWithoutDetails_returnsEmptyList() {
        when(commonCodeRepository.findById("EMPTY_TYPE")).thenReturn(Optional.of(commonCode));
        when(commonCodeDetailRepository.findByCommonCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(
                commonCode, true)).thenReturn(List.of());

        List<CommonCodeDetail> result = commonCodeReader.findActiveDetails("EMPTY_TYPE");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("공통 코드가 없으면 NOT_FOUND를 반환한다")
    void findActiveDetails_missingType_throwsNotFound() {
        when(commonCodeRepository.findById("MISSING_TYPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commonCodeReader.findActiveDetails("MISSING_TYPE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(commonCodeDetailRepository, never())
                .findByCommonCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(any(), any());
    }
}
