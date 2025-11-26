package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.dto.CodeDetailRequest;
import com.weedrice.whiteboard.global.common.dto.CodeTypeCreateRequest;
import com.weedrice.whiteboard.global.common.entity.CommonCode;
import com.weedrice.whiteboard.global.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.global.common.repository.CommonCodeDetailRepository;
import com.weedrice.whiteboard.global.common.repository.CommonCodeRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonCodeService {

    private final CommonCodeRepository commonCodeRepository;
    private final CommonCodeDetailRepository commonCodeDetailRepository;

    public List<CommonCodeDetail> getCommonCodes(String typeCode) {
        return commonCodeDetailRepository.findActiveDetailsByTypeCode(typeCode);
    }

    @Transactional
    public CommonCode createCodeType(CodeTypeCreateRequest request) {
        if (commonCodeRepository.existsById(request.getTypeCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미 존재하는 코드 타입입니다.");
        }
        CommonCode commonCode = new CommonCode(request.getTypeCode(), request.getTypeName(), request.getDescription());
        return commonCodeRepository.save(commonCode);
    }

    @Transactional
    public CommonCodeDetail createCodeDetail(String typeCode, CodeDetailRequest request) {
        CommonCode commonCode = commonCodeRepository.findById(typeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 코드 타입입니다."));

        CommonCodeDetail detail = CommonCodeDetail.builder()
                .commonCode(commonCode)
                .codeValue(request.getCodeValue())
                .codeName(request.getCodeName())
                .sortOrder(request.getSortOrder())
                .isActive(request.getIsActive())
                .build();
        return commonCodeDetailRepository.save(detail);
    }

    @Transactional
    public CommonCodeDetail updateCodeDetail(Long detailId, CodeDetailRequest request) {
        CommonCodeDetail detail = commonCodeDetailRepository.findById(detailId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 상세 코드입니다."));
        detail.update(request.getCodeValue(), request.getCodeName(), request.getSortOrder(), request.getIsActive());
        return detail;
    }

    @Transactional
    public void deleteCodeDetail(Long detailId) {
        if (!commonCodeDetailRepository.existsById(detailId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 상세 코드입니다.");
        }
        commonCodeDetailRepository.deleteById(detailId);
    }
}
