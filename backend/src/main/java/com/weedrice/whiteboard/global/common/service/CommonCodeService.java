package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.global.common.repository.CommonCodeDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonCodeService {

    private final CommonCodeDetailRepository commonCodeDetailRepository;

    public List<CommonCodeDetail> getCommonCodes(String typeCode) {
        return commonCodeDetailRepository.findActiveDetailsByTypeCode(typeCode);
    }
}
