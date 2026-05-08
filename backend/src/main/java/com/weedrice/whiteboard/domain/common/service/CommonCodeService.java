package com.weedrice.whiteboard.domain.common.service;

import com.weedrice.whiteboard.domain.common.dto.*;
import com.weedrice.whiteboard.domain.common.entity.CommonCode;
import com.weedrice.whiteboard.domain.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeDetailRepository;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
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
        if (commonCodeRepository.existsById(request.getTypeCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        CommonCode commonCode = CommonCode.builder()
                .typeCode(request.getTypeCode())
                .typeName(request.getTypeName())
                .description(request.getDescription())
                .build();

        try {
            return CommonCodeResponse.from(commonCodeRepository.saveAndFlush(commonCode));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    public List<CommonCodeResponse> getAllCommonCodes() {
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
        CommonCode commonCode = commonCodeRepository.findById(typeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        commonCode.update(request.getTypeName(), request.getDescription());
        return CommonCodeResponse.from(commonCode);
    }

    // --- Common Code Detail ---

    @Transactional
    public CommonCodeDetailResponse createCommonCodeDetail(String typeCode, CommonCodeDetailRequest request) {
        CommonCode commonCode = commonCodeRepository.findById(typeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        var existingDetail = commonCodeDetailRepository
                .findByCommonCode_TypeCodeAndCodeValue(typeCode, request.getCodeValue());
        if (existingDetail.isPresent()) {
            CommonCodeDetail detail = existingDetail.get();
            if (Boolean.TRUE.equals(detail.getIsActive())) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
            detail.update(
                    request.getCodeName(),
                    request.getSortOrder() != null ? request.getSortOrder() : 0,
                    request.getIsActive() != null ? request.getIsActive() : true);
            return CommonCodeDetailResponse.from(detail);
        }

        CommonCodeDetail detail = CommonCodeDetail.builder()
                .commonCode(commonCode)
                .codeValue(request.getCodeValue())
                .codeName(request.getCodeName())
                .sortOrder(request.getSortOrder())
                .isActive(request.getIsActive())
                .build();

        try {
            return CommonCodeDetailResponse.from(commonCodeDetailRepository.saveAndFlush(detail));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    public List<CommonCodeDetailResponse> getCommonCodeDetails(String typeCode) {
        if (!commonCodeRepository.existsById(typeCode)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return commonCodeDetailRepository.findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAsc(typeCode, true).stream()
                .map(CommonCodeDetailResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommonCodeDetailResponse updateCommonCodeDetail(Long id, CommonCodeDetailRequest request) {
        CommonCodeDetail detail = commonCodeDetailRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!Objects.equals(detail.getCodeValue(), request.getCodeValue())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Boolean isActive = request.getIsActive() != null ? request.getIsActive() : detail.getIsActive();
        detail.update(request.getCodeName(), request.getSortOrder(), isActive);
        return CommonCodeDetailResponse.from(detail);
    }
    
    @Transactional
    public void deleteCommonCodeDetail(Long id) {
        CommonCodeDetail detail = commonCodeDetailRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        detail.deactivate();
    }
}
