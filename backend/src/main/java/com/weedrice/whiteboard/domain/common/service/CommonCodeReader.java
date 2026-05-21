package com.weedrice.whiteboard.domain.common.service;

import com.weedrice.whiteboard.domain.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeDetailRepository;
import com.weedrice.whiteboard.domain.common.repository.CommonCodeRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CommonCodeReader {

    private final CommonCodeRepository commonCodeRepository;
    private final CommonCodeDetailRepository commonCodeDetailRepository;

    public List<CommonCodeDetail> findActiveDetails(String typeCode) {
        List<CommonCodeDetail> details = commonCodeDetailRepository
                .findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAscCodeValueAsc(typeCode, true);
        if (!details.isEmpty() || commonCodeRepository.existsById(typeCode)) {
            return details;
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }
}
