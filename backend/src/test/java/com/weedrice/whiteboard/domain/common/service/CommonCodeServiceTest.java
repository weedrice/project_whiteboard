package com.weedrice.whiteboard.domain.common.service;

import com.weedrice.whiteboard.domain.common.dto.CommonCodeDetailRequest;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeDetailResponse;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeRequest;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeResponse;
import com.weedrice.whiteboard.domain.common.entity.CommonCode;
import com.weedrice.whiteboard.domain.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeDetailRepository;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommonCodeServiceTest {

    @Mock
    private CommonCodeRepository commonCodeRepository;

    @Mock
    private CommonCodeDetailRepository commonCodeDetailRepository;

    @InjectMocks
    private CommonCodeService commonCodeService;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;
    private CommonCode commonCode;
    private CommonCodeDetail commonCodeDetail;

    @BeforeEach
    void setUp() {
        mockedSecurityUtils = mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);

        commonCode = CommonCode.builder()
                .typeCode("TEST_TYPE")
                .typeName("Test Type")
                .description("Test Description")
                .build();

        commonCodeDetail = CommonCodeDetail.builder()
                .commonCode(commonCode)
                .codeValue("TEST_VALUE")
                .codeName("Test Value")
                .sortOrder(1)
                .isActive(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("공통 코드 생성 성공")
    void createCommonCode_success() {
        // given
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeCode", "NEW_TYPE");
        ReflectionTestUtils.setField(request, "typeName", "New Type");
        ReflectionTestUtils.setField(request, "description", "New Description");

        when(commonCodeRepository.existsById("NEW_TYPE")).thenReturn(false);
        when(commonCodeRepository.save(any(CommonCode.class))).thenAnswer(invocation -> {
            CommonCode code = invocation.getArgument(0);
            return code;
        });

        // when
        CommonCodeResponse response = commonCodeService.createCommonCode(request);

        // then
        assertThat(response).isNotNull();
        verify(commonCodeRepository).save(any(CommonCode.class));
    }

    @Test
    @DisplayName("공통 코드 생성 실패 - 중복된 타입 코드")
    void createCommonCode_fail_duplicate() {
        // given
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeCode", "EXISTING_TYPE");
        ReflectionTestUtils.setField(request, "typeName", "Existing Type");

        when(commonCodeRepository.existsById("EXISTING_TYPE")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> commonCodeService.createCommonCode(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("모든 공통 코드 조회 성공")
    void getAllCommonCodes_success() {
        // given
        CommonCode code1 = CommonCode.builder().typeCode("TYPE1").typeName("Type 1").build();
        CommonCode code2 = CommonCode.builder().typeCode("TYPE2").typeName("Type 2").build();
        when(commonCodeRepository.findAll()).thenReturn(Arrays.asList(code1, code2));

        // when
        List<CommonCodeResponse> responses = commonCodeService.getAllCommonCodes();

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("공통 코드 조회 성공")
    void getCommonCode_success() {
        // given
        when(commonCodeRepository.findById("TEST_TYPE")).thenReturn(Optional.of(commonCode));

        // when
        CommonCodeResponse response = commonCodeService.getCommonCode("TEST_TYPE");

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTypeCode()).isEqualTo("TEST_TYPE");
    }

    @Test
    @DisplayName("공통 코드 조회 실패 - 존재하지 않음")
    void getCommonCode_fail_notFound() {
        // given
        when(commonCodeRepository.findById("NON_EXISTENT")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commonCodeService.getCommonCode("NON_EXISTENT"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("공통 코드 수정 성공")
    void updateCommonCode_success() {
        // given
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeName", "Updated Type");
        ReflectionTestUtils.setField(request, "description", "Updated Description");

        when(commonCodeRepository.findById("TEST_TYPE")).thenReturn(Optional.of(commonCode));

        // when
        CommonCodeResponse response = commonCodeService.updateCommonCode("TEST_TYPE", request);

        // then
        assertThat(response).isNotNull();
        verify(commonCodeRepository).findById("TEST_TYPE");
    }

    @Test
    @DisplayName("공통 코드 상세 생성 성공")
    void createCommonCodeDetail_success() {
        // given
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeValue", "NEW_VALUE");
        ReflectionTestUtils.setField(request, "codeName", "New Value");
        ReflectionTestUtils.setField(request, "sortOrder", 1);
        ReflectionTestUtils.setField(request, "isActive", true);

        when(commonCodeRepository.findById("TEST_TYPE")).thenReturn(Optional.of(commonCode));
        when(commonCodeDetailRepository.findByCommonCode_TypeCodeAndCodeValue("TEST_TYPE", "NEW_VALUE"))
                .thenReturn(Optional.empty());
        when(commonCodeDetailRepository.save(any(CommonCodeDetail.class))).thenAnswer(invocation -> {
            CommonCodeDetail detail = invocation.getArgument(0);
            return detail;
        });

        // when
        CommonCodeDetailResponse response = commonCodeService.createCommonCodeDetail("TEST_TYPE", request);

        // then
        assertThat(response).isNotNull();
        verify(commonCodeDetailRepository).save(any(CommonCodeDetail.class));
    }

    @Test
    @DisplayName("공통 코드 상세 목록 조회 성공")
    void getCommonCodeDetails_success() {
        // given
        when(commonCodeDetailRepository.findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAsc("TEST_TYPE", true))
                .thenReturn(Arrays.asList(commonCodeDetail));

        // when
        List<CommonCodeDetailResponse> responses = commonCodeService.getCommonCodeDetails("TEST_TYPE");

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("공통 코드 상세 수정 성공")
    void updateCommonCodeDetail_success() {
        // given
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeName", "Updated Value");
        ReflectionTestUtils.setField(request, "sortOrder", 2);
        ReflectionTestUtils.setField(request, "isActive", false);

        when(commonCodeDetailRepository.findById(1L)).thenReturn(Optional.of(commonCodeDetail));

        // when
        CommonCodeDetailResponse response = commonCodeService.updateCommonCodeDetail(1L, request);

        // then
        assertThat(response).isNotNull();
        verify(commonCodeDetailRepository).findById(1L);
    }

    @Test
    @DisplayName("공통 코드 상세 삭제 성공")
    void deleteCommonCodeDetail_success() {
        // given
        when(commonCodeDetailRepository.findById(1L)).thenReturn(Optional.of(commonCodeDetail));

        // when
        commonCodeService.deleteCommonCodeDetail(1L);

        // then
        verify(commonCodeDetailRepository).delete(commonCodeDetail);
    }
}
