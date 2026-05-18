package com.weedrice.whiteboard.domain.common.service;

import com.weedrice.whiteboard.domain.common.dto.CommonCodeDetailRequest;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeDetailResponse;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeRequest;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
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

    @Mock
    private CommonCodeReader commonCodeReader;

    @InjectMocks
    private CommonCodeService commonCodeService;

    private CommonCode commonCode;
    private CommonCodeDetail commonCodeDetail;

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("공통 코드 생성 성공")
    void createCommonCode_success() {
        // given
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeCode", "NEW_TYPE");
        ReflectionTestUtils.setField(request, "typeName", "New Type");
        ReflectionTestUtils.setField(request, "description", "New Description");

        when(commonCodeRepository.existsById("NEW_TYPE")).thenReturn(false);
        when(commonCodeRepository.saveAndFlush(any(CommonCode.class))).thenAnswer(invocation -> {
            CommonCode code = invocation.getArgument(0);
            return code;
        });

        // when
        CommonCodeResponse response = commonCodeService.createCommonCode(request);

        // then
        assertThat(response).isNotNull();
        verify(commonCodeRepository).saveAndFlush(any(CommonCode.class));
    }

    @Test
    @DisplayName("공통 코드 생성은 설명을 trim 해서 저장한다")
    void createCommonCode_trimsDescription() {
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeCode", "NEW_TYPE");
        ReflectionTestUtils.setField(request, "typeName", "New Type");
        ReflectionTestUtils.setField(request, "description", "  New Description  ");

        when(commonCodeRepository.existsById("NEW_TYPE")).thenReturn(false);
        when(commonCodeRepository.saveAndFlush(any(CommonCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommonCodeResponse response = commonCodeService.createCommonCode(request);

        assertThat(response.getDescription()).isEqualTo("New Description");
    }

    @Test
    @DisplayName("공통 코드 생성은 타입 코드와 타입 이름을 trim 해서 저장한다")
    void createCommonCode_trimsRequiredFields() {
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeCode", "  NEW_TYPE  ");
        ReflectionTestUtils.setField(request, "typeName", "  New Type  ");

        when(commonCodeRepository.existsById("NEW_TYPE")).thenReturn(false);
        when(commonCodeRepository.saveAndFlush(any(CommonCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommonCodeResponse response = commonCodeService.createCommonCode(request);

        assertThat(response.getTypeCode()).isEqualTo("NEW_TYPE");
        assertThat(response.getTypeName()).isEqualTo("New Type");
        verify(commonCodeRepository).existsById("NEW_TYPE");
    }

    @Test
    @DisplayName("공통 코드 생성은 blank 타입 이름을 거부한다")
    void createCommonCode_blankTypeName_invalidInput() {
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeCode", "NEW_TYPE");
        ReflectionTestUtils.setField(request, "typeName", "   ");

        assertThatThrownBy(() -> commonCodeService.createCommonCode(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(commonCodeRepository, never()).saveAndFlush(any(CommonCode.class));
    }

    @Test
    @DisplayName("공통 코드 생성은 설명이 255자를 초과하면 거부한다")
    void createCommonCode_descriptionTooLong_invalidInput() {
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeCode", "NEW_TYPE");
        ReflectionTestUtils.setField(request, "typeName", "New Type");
        ReflectionTestUtils.setField(request, "description", "a".repeat(256));

        assertThatThrownBy(() -> commonCodeService.createCommonCode(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(commonCodeRepository, never()).saveAndFlush(any(CommonCode.class));
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
    @DisplayName("공통 코드 생성 충돌을 중복 리소스 오류로 변환한다")
    void createCommonCode_conflictDuringSave_throwsDuplicateResource() {
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeCode", "RACE_TYPE");
        ReflectionTestUtils.setField(request, "typeName", "Race Type");

        when(commonCodeRepository.existsById("RACE_TYPE")).thenReturn(false);
        when(commonCodeRepository.saveAndFlush(any(CommonCode.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

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
        when(commonCodeRepository.findAllByOrderByTypeCodeAsc()).thenReturn(Arrays.asList(code1, code2));

        // when
        List<CommonCodeResponse> responses = commonCodeService.getAllCommonCodes();

        // then
        assertThat(responses).hasSize(2);
        verify(commonCodeRepository).findAllByOrderByTypeCodeAsc();
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
        verify(commonCodeRepository).findById("TEST_TYPE");
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
        verify(commonCodeRepository).findById("NON_EXISTENT");
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
    @DisplayName("공통 코드 수정은 설명이 255자를 초과하면 거부한다")
    void updateCommonCode_descriptionTooLong_invalidInput() {
        CommonCodeRequest request = new CommonCodeRequest();
        ReflectionTestUtils.setField(request, "typeName", "Updated Type");
        ReflectionTestUtils.setField(request, "description", "a".repeat(256));

        assertThatThrownBy(() -> commonCodeService.updateCommonCode("TEST_TYPE", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(commonCode.getDescription()).isEqualTo("Test Description");
        verify(commonCodeRepository, never()).findById(anyString());
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
        when(commonCodeDetailRepository.saveAndFlush(any(CommonCodeDetail.class))).thenAnswer(invocation -> {
            CommonCodeDetail detail = invocation.getArgument(0);
            return detail;
        });

        // when
        CommonCodeDetailResponse response = commonCodeService.createCommonCodeDetail("TEST_TYPE", request);

        // then
        assertThat(response).isNotNull();
        verify(commonCodeDetailRepository).saveAndFlush(any(CommonCodeDetail.class));
    }

    @Test
    @DisplayName("공통 코드 상세 생성 서비스 직접 호출은 값을 trim 하고 null sortOrder를 0으로 저장한다")
    void createCommonCodeDetail_serviceCall_trimsValuesAndDefaultsNullSortOrder() {
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeValue", "  NEW_VALUE  ");
        ReflectionTestUtils.setField(request, "codeName", "  New Value  ");

        when(commonCodeRepository.findById("TEST_TYPE")).thenReturn(Optional.of(commonCode));
        when(commonCodeDetailRepository.findByCommonCode_TypeCodeAndCodeValue("TEST_TYPE", "NEW_VALUE"))
                .thenReturn(Optional.empty());
        when(commonCodeDetailRepository.saveAndFlush(any(CommonCodeDetail.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CommonCodeDetailResponse response = commonCodeService.createCommonCodeDetail(" TEST_TYPE ", request);

        assertThat(response.getCodeValue()).isEqualTo("NEW_VALUE");
        assertThat(response.getCodeName()).isEqualTo("New Value");
        assertThat(response.getSortOrder()).isZero();
        verify(commonCodeRepository).findById("TEST_TYPE");
        verify(commonCodeDetailRepository).findByCommonCode_TypeCodeAndCodeValue("TEST_TYPE", "NEW_VALUE");
    }

    @Test
    @DisplayName("공통 코드 상세 생성 충돌을 중복 리소스 오류로 변환한다")
    void createCommonCodeDetail_conflictDuringSave_throwsDuplicateResource() {
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeValue", "RACE_VALUE");
        ReflectionTestUtils.setField(request, "codeName", "Race Value");
        ReflectionTestUtils.setField(request, "sortOrder", 1);
        ReflectionTestUtils.setField(request, "isActive", true);

        when(commonCodeRepository.findById("TEST_TYPE")).thenReturn(Optional.of(commonCode));
        when(commonCodeDetailRepository.findByCommonCode_TypeCodeAndCodeValue("TEST_TYPE", "RACE_VALUE"))
                .thenReturn(Optional.empty());
        when(commonCodeDetailRepository.saveAndFlush(any(CommonCodeDetail.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> commonCodeService.createCommonCodeDetail("TEST_TYPE", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("비활성 상세 코드가 있으면 기존 row를 복원한다")
    void createCommonCodeDetail_reactivatesInactiveDetail() {
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeValue", "TEST_VALUE");
        ReflectionTestUtils.setField(request, "codeName", "Restored Value");
        ReflectionTestUtils.setField(request, "sortOrder", 2);
        ReflectionTestUtils.setField(request, "isActive", true);
        ReflectionTestUtils.setField(commonCodeDetail, "isActive", false);

        when(commonCodeRepository.findById("TEST_TYPE")).thenReturn(Optional.of(commonCode));
        when(commonCodeDetailRepository.findByCommonCode_TypeCodeAndCodeValue("TEST_TYPE", "TEST_VALUE"))
                .thenReturn(Optional.of(commonCodeDetail));

        CommonCodeDetailResponse response = commonCodeService.createCommonCodeDetail("TEST_TYPE", request);

        assertThat(response.getCodeName()).isEqualTo("Restored Value");
        assertThat(response.getSortOrder()).isEqualTo(2);
        assertThat(response.getIsActive()).isTrue();
        verify(commonCodeDetailRepository, never()).saveAndFlush(any(CommonCodeDetail.class));
    }

    @Test
    @DisplayName("공통 코드 상세 목록 조회 성공")
    void getCommonCodeDetails_success() {
        // given
        when(commonCodeReader.findActiveDetails("TEST_TYPE")).thenReturn(Arrays.asList(commonCodeDetail));

        // when
        List<CommonCodeDetailResponse> responses = commonCodeService.getCommonCodeDetails("TEST_TYPE");

        // then
        assertThat(responses).hasSize(1);
        verify(commonCodeReader).findActiveDetails("TEST_TYPE");
    }

    @Test
    @DisplayName("공통 코드가 있으면 상세 코드가 없어도 빈 목록을 반환한다")
    void getCommonCodeDetails_existingTypeWithoutDetails_returnsEmptyList() {
        when(commonCodeReader.findActiveDetails("EMPTY_TYPE")).thenReturn(List.of());

        List<CommonCodeDetailResponse> responses = commonCodeService.getCommonCodeDetails("EMPTY_TYPE");

        assertThat(responses).isEmpty();
        verify(commonCodeReader).findActiveDetails("EMPTY_TYPE");
    }

    @Test
    @DisplayName("공통 코드가 없으면 상세 목록 조회는 NOT_FOUND를 반환한다")
    void getCommonCodeDetails_missingType_throwsNotFound() {
        when(commonCodeReader.findActiveDetails("MISSING_TYPE"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> commonCodeService.getCommonCodeDetails("MISSING_TYPE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(commonCodeReader).findActiveDetails("MISSING_TYPE");
    }

    @Test
    @DisplayName("공통 코드 상세 수정 성공")
    void updateCommonCodeDetail_success() {
        // given
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeValue", "TEST_VALUE");
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
    @DisplayName("공통 코드 상세 수정 시 isActive가 없으면 기존 상태를 유지한다")
    void updateCommonCodeDetail_nullIsActive_keepsCurrentValue() {
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeValue", "TEST_VALUE");
        ReflectionTestUtils.setField(request, "codeName", "Updated Value");
        ReflectionTestUtils.setField(request, "sortOrder", 2);

        when(commonCodeDetailRepository.findById(1L)).thenReturn(Optional.of(commonCodeDetail));

        CommonCodeDetailResponse response = commonCodeService.updateCommonCodeDetail(1L, request);

        assertThat(response.getIsActive()).isTrue();
        assertThat(commonCodeDetail.getIsActive()).isTrue();
        verify(commonCodeDetailRepository).findById(1L);
    }

    @Test
    @DisplayName("공통 코드 상세 수정 서비스 직접 호출은 값을 trim 하고 null sortOrder를 0으로 저장한다")
    void updateCommonCodeDetail_serviceCall_trimsValuesAndDefaultsNullSortOrder() {
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeValue", "  TEST_VALUE  ");
        ReflectionTestUtils.setField(request, "codeName", "  Updated Value  ");

        when(commonCodeDetailRepository.findById(1L)).thenReturn(Optional.of(commonCodeDetail));

        CommonCodeDetailResponse response = commonCodeService.updateCommonCodeDetail(1L, request);

        assertThat(response.getCodeName()).isEqualTo("Updated Value");
        assertThat(response.getSortOrder()).isZero();
        assertThat(commonCodeDetail.getCodeName()).isEqualTo("Updated Value");
        assertThat(commonCodeDetail.getSortOrder()).isZero();
    }

    @Test
    @DisplayName("공통 코드 상세 수정 시 코드값 변경 요청은 검증 오류로 거부한다")
    void updateCommonCodeDetail_changedCodeValue_throwsValidationError() {
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        ReflectionTestUtils.setField(request, "codeValue", "CHANGED_VALUE");
        ReflectionTestUtils.setField(request, "codeName", "Updated Value");
        ReflectionTestUtils.setField(request, "sortOrder", 2);

        when(commonCodeDetailRepository.findById(1L)).thenReturn(Optional.of(commonCodeDetail));

        assertThatThrownBy(() -> commonCodeService.updateCommonCodeDetail(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        assertThat(commonCodeDetail.getCodeValue()).isEqualTo("TEST_VALUE");
        assertThat(commonCodeDetail.getCodeName()).isEqualTo("Test Value");
    }

    @Test
    @DisplayName("공통 코드 상세 삭제는 비활성화로 처리한다")
    void deleteCommonCodeDetail_deactivatesDetail() {
        // given
        when(commonCodeDetailRepository.findById(1L)).thenReturn(Optional.of(commonCodeDetail));

        // when
        commonCodeService.deleteCommonCodeDetail(1L);

        // then
        assertThat(commonCodeDetail.getIsActive()).isFalse();
        verify(commonCodeDetailRepository, never()).delete(commonCodeDetail);
    }
}
