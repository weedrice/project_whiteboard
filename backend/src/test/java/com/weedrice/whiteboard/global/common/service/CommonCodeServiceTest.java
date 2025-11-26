package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.dto.CodeDetailRequest;
import com.weedrice.whiteboard.global.common.dto.CodeTypeCreateRequest;
import com.weedrice.whiteboard.global.common.entity.CommonCode;
import com.weedrice.whiteboard.global.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.global.common.repository.CommonCodeDetailRepository;
import com.weedrice.whiteboard.global.common.repository.CommonCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonCodeServiceTest {

    @Mock
    private CommonCodeRepository commonCodeRepository;
    @Mock
    private CommonCodeDetailRepository commonCodeDetailRepository;

    @InjectMocks
    private CommonCodeService commonCodeService;

    @Test
    @DisplayName("코드 타입 생성 성공")
    void createCodeType_success() {
        // given
        CodeTypeCreateRequest request = new CodeTypeCreateRequest("TEST_CODE", "Test Code", "Description");
        when(commonCodeRepository.existsById("TEST_CODE")).thenReturn(false);
        when(commonCodeRepository.save(any(CommonCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CommonCode commonCode = commonCodeService.createCodeType(request);

        // then
        assertThat(commonCode.getTypeCode()).isEqualTo("TEST_CODE");
        verify(commonCodeRepository).save(any(CommonCode.class));
    }

    @Test
    @DisplayName("상세 코드 생성 성공")
    void createCodeDetail_success() {
        // given
        String typeCode = "TEST_CODE";
        CodeDetailRequest request = new CodeDetailRequest("VALUE", "Name", 1, true);
        CommonCode commonCode = new CommonCode("TEST_CODE", "Test Code", "Description");
        when(commonCodeRepository.findById(typeCode)).thenReturn(Optional.of(commonCode));
        when(commonCodeDetailRepository.save(any(CommonCodeDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CommonCodeDetail detail = commonCodeService.createCodeDetail(typeCode, request);

        // then
        assertThat(detail.getCodeValue()).isEqualTo("VALUE");
        verify(commonCodeDetailRepository).save(any(CommonCodeDetail.class));
    }
}
