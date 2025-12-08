package com.weedrice.whiteboard.domain.common.service;

import com.weedrice.whiteboard.domain.common.dto.*;
import com.weedrice.whiteboard.domain.common.entity.CommonCode;
import com.weedrice.whiteboard.domain.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeDetailRepository;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonCodeService {

    private final CommonCodeRepository commonCodeRepository;
    private final CommonCodeDetailRepository commonCodeDetailRepository;

    // --- Common Code (Master) ---

    @Transactional
    public CommonCodeResponse createCommonCode(CommonCodeRequest request) {
        SecurityUtils.validateSuperAdminPermission();

        if (commonCodeRepository.existsById(request.getTypeCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        CommonCode commonCode = CommonCode.builder()
                .typeCode(request.getTypeCode())
                .typeName(request.getTypeName())
                .description(request.getDescription())
                .build();
        
        return CommonCodeResponse.from(commonCodeRepository.save(commonCode));
    }

    public List<CommonCodeResponse> getAllCommonCodes() {
        // Admin only? Or anyone? Assuming admin for management
        SecurityUtils.validateSuperAdminPermission();
        return commonCodeRepository.findAll().stream()
                .map(CommonCodeResponse::from)
                .collect(Collectors.toList());
    }

    public CommonCodeResponse getCommonCode(String typeCode) {
        CommonCode commonCode = commonCodeRepository.findById(typeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return CommonCodeResponse.from(commonCode);
    }

    @Transactional
    public CommonCodeResponse updateCommonCode(String typeCode, CommonCodeRequest request) {
        SecurityUtils.validateSuperAdminPermission();

        CommonCode commonCode = commonCodeRepository.findById(typeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        commonCode.update(request.getTypeName(), request.getDescription());
        return CommonCodeResponse.from(commonCode);
    }

    // --- Common Code Detail ---

    @Transactional
    public CommonCodeDetailResponse createCommonCodeDetail(String typeCode, CommonCodeDetailRequest request) {
        SecurityUtils.validateSuperAdminPermission();

        CommonCode commonCode = commonCodeRepository.findById(typeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (commonCodeDetailRepository.findByCommonCode_TypeCodeAndCodeValue(typeCode, request.getCodeValue()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        CommonCodeDetail detail = CommonCodeDetail.builder()
                .commonCode(commonCode)
                .codeValue(request.getCodeValue())
                .codeName(request.getCodeName())
                .sortOrder(request.getSortOrder())
                .isActive(request.getIsActive())
                .build();

        return CommonCodeDetailResponse.from(commonCodeDetailRepository.save(detail));
    }

    public List<CommonCodeDetailResponse> getCommonCodeDetails(String typeCode) {
        // Public access allowed for retrieving active codes
        return commonCodeDetailRepository.findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAsc(typeCode, true).stream()
                .map(CommonCodeDetailResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommonCodeDetailResponse updateCommonCodeDetail(Long id, CommonCodeDetailRequest request) {
        SecurityUtils.validateSuperAdminPermission();

        CommonCodeDetail detail = commonCodeDetailRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        detail.update(request.getCodeName(), request.getSortOrder(), request.getIsActive());
        return CommonCodeDetailResponse.from(detail);
    }
    
    @Transactional
    public void deleteCommonCodeDetail(Long id) {
        SecurityUtils.validateSuperAdminPermission();
        
        CommonCodeDetail detail = commonCodeDetailRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        // Hard delete or Soft delete? Generally codes are not deleted but deactivated.
        // Let's implement hard delete for now as per request, but safer to just deactivate via update.
        commonCodeDetailRepository.delete(detail);
    }
}
