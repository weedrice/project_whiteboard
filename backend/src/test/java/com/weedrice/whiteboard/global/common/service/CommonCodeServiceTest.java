package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.dto.CodeTypeCreateRequest;
import com.weedrice.whiteboard.global.common.entity.CommonCode;
import com.weedrice.whiteboard.global.common.repository.CommonCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonCodeServiceTest {

    @Mock
    private CommonCodeRepository commonCodeRepository;

    @InjectMocks
    private CommonCodeService commonCodeService;

    @Test
    @DisplayName("공통 코드 타입 생성 성공")
    void createCodeType_success() {
        // given
        CodeTypeCreateRequest request = new CodeTypeCreateRequest("TEST_CODE", "Test Code", "This is a test code.");
        CommonCode commonCode = new CommonCode(request.getTypeCode(), request.getTypeName(), request.getDescription());

        when(commonCodeRepository.existsById(request.getTypeCode())).thenReturn(false);
        when(commonCodeRepository.save(any(CommonCode.class))).thenReturn(commonCode);

        // when
        CommonCode createdCodeType = commonCodeService.createCodeType(request);

        // then
        assertThat(createdCodeType.getTypeCode()).isEqualTo("TEST_CODE");
        verify(commonCodeRepository).save(any(CommonCode.class));
    }
}
