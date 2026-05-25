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
        when(commonCodeDetailRepository.findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(
                "TEST_TYPE", true)).thenReturn(List.of(commonCodeDetail));

        List<CommonCodeDetail> result = commonCodeReader.findActiveDetails("TEST_TYPE");

        assertThat(result).containsExactly(commonCodeDetail);
        verify(commonCodeRepository, never()).existsById("TEST_TYPE");
        verify(commonCodeRepository, never()).findById(any());
        verify(commonCodeDetailRepository).findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(
                "TEST_TYPE", true);
    }

    @Test
    @DisplayName("공통 코드가 있으면 상세가 없어도 빈 목록을 반환한다")
    void findActiveDetails_existingTypeWithoutDetails_returnsEmptyList() {
        when(commonCodeDetailRepository.findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(
                "EMPTY_TYPE", true)).thenReturn(List.of());
        when(commonCodeRepository.existsById("EMPTY_TYPE")).thenReturn(true);

        List<CommonCodeDetail> result = commonCodeReader.findActiveDetails("EMPTY_TYPE");

        assertThat(result).isEmpty();
        verify(commonCodeRepository).existsById("EMPTY_TYPE");
        verify(commonCodeRepository, never()).findById(any());
    }

    @Test
    @DisplayName("공통 코드가 없으면 NOT_FOUND를 반환한다")
    void findActiveDetails_missingType_throwsNotFound() {
        when(commonCodeDetailRepository.findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(
                "MISSING_TYPE", true)).thenReturn(List.of());
        when(commonCodeRepository.existsById("MISSING_TYPE")).thenReturn(false);

        assertThatThrownBy(() -> commonCodeReader.findActiveDetails("MISSING_TYPE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(commonCodeRepository).existsById("MISSING_TYPE");
        verify(commonCodeRepository, never()).findById(any());
    }
}
