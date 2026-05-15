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

    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int MAX_TYPE_CODE_LENGTH = 50;
    private static final int MAX_TYPE_NAME_LENGTH = 100;
    private static final int MAX_CODE_VALUE_LENGTH = 100;
    private static final int MAX_CODE_NAME_LENGTH = 100;

    private final CommonCodeRepository commonCodeRepository;
    private final CommonCodeDetailRepository commonCodeDetailRepository;

    // --- Common Code (Master) ---

    @Transactional
    public CommonCodeResponse createCommonCode(CommonCodeRequest request) {
        String typeCode = normalizeRequired(request.getTypeCode(), MAX_TYPE_CODE_LENGTH);
        String typeName = normalizeRequired(request.getTypeName(), MAX_TYPE_NAME_LENGTH);
        String description = normalizeDescription(request.getDescription());

        if (commonCodeRepository.existsById(typeCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        CommonCode commonCode = CommonCode.builder()
                .typeCode(typeCode)
                .typeName(typeName)
                .description(description)
                .build();

        try {
            return CommonCodeResponse.from(commonCodeRepository.saveAndFlush(commonCode));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    public List<CommonCodeResponse> getAllCommonCodes() {
        return commonCodeRepository.findAllByOrderByTypeCodeAsc().stream()
                .map(CommonCodeResponse::from)
                .collect(Collectors.toList());
    }

    public CommonCodeResponse getCommonCode(String typeCode) {
        String normalizedTypeCode = normalizeRequired(typeCode, MAX_TYPE_CODE_LENGTH);
        CommonCode commonCode = commonCodeRepository.findById(normalizedTypeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return CommonCodeResponse.from(commonCode);
    }

    @Transactional
    public CommonCodeResponse updateCommonCode(String typeCode, CommonCodeRequest request) {
        String normalizedTypeCode = normalizeRequired(typeCode, MAX_TYPE_CODE_LENGTH);
        String typeName = normalizeRequired(request.getTypeName(), MAX_TYPE_NAME_LENGTH);
        String description = normalizeDescription(request.getDescription());

        CommonCode commonCode = commonCodeRepository.findById(normalizedTypeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        commonCode.update(typeName, description);
        return CommonCodeResponse.from(commonCode);
    }

    private String normalizeRequired(String value, int maxLength) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalized = value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    // --- Common Code Detail ---

    @Transactional
    public CommonCodeDetailResponse createCommonCodeDetail(String typeCode, CommonCodeDetailRequest request) {
        String normalizedTypeCode = normalizeRequired(typeCode, MAX_TYPE_CODE_LENGTH);
        String codeValue = normalizeRequired(request.getCodeValue(), MAX_CODE_VALUE_LENGTH);
        String codeName = normalizeRequired(request.getCodeName(), MAX_CODE_NAME_LENGTH);
        Integer sortOrder = normalizeSortOrder(request.getSortOrder());

        CommonCode commonCode = commonCodeRepository.findById(normalizedTypeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        var existingDetail = commonCodeDetailRepository
                .findByCommonCode_TypeCodeAndCodeValue(normalizedTypeCode, codeValue);
        if (existingDetail.isPresent()) {
            CommonCodeDetail detail = existingDetail.get();
            if (Boolean.TRUE.equals(detail.getIsActive())) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
            detail.update(
                    codeName,
                    sortOrder,
                    request.getIsActive() != null ? request.getIsActive() : true);
            return CommonCodeDetailResponse.from(detail);
        }

        CommonCodeDetail detail = CommonCodeDetail.builder()
                .commonCode(commonCode)
                .codeValue(codeValue)
                .codeName(codeName)
                .sortOrder(sortOrder)
                .isActive(request.getIsActive())
                .build();

        try {
            return CommonCodeDetailResponse.from(commonCodeDetailRepository.saveAndFlush(detail));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    public List<CommonCodeDetailResponse> getCommonCodeDetails(String typeCode) {
        String normalizedTypeCode = normalizeRequired(typeCode, MAX_TYPE_CODE_LENGTH);
        if (!commonCodeRepository.existsById(normalizedTypeCode)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return commonCodeDetailRepository.findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(
                        normalizedTypeCode, true).stream()
                .map(CommonCodeDetailResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommonCodeDetailResponse updateCommonCodeDetail(Long id, CommonCodeDetailRequest request) {
        CommonCodeDetail detail = commonCodeDetailRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        String codeValue = normalizeRequired(request.getCodeValue(), MAX_CODE_VALUE_LENGTH);
        String codeName = normalizeRequired(request.getCodeName(), MAX_CODE_NAME_LENGTH);
        Integer sortOrder = normalizeSortOrder(request.getSortOrder());

        if (!Objects.equals(detail.getCodeValue(), codeValue)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Boolean isActive = request.getIsActive() != null ? request.getIsActive() : detail.getIsActive();
        detail.update(codeName, sortOrder, isActive);
        return CommonCodeDetailResponse.from(detail);
    }

    private Integer normalizeSortOrder(Integer sortOrder) {
        return sortOrder != null ? sortOrder : 0;
    }
    
    @Transactional
    public void deleteCommonCodeDetail(Long id) {
        CommonCodeDetail detail = commonCodeDetailRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        detail.deactivate();
    }
}
