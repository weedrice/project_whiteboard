package com.weedrice.whiteboard.domain.common.dto;

import com.weedrice.whiteboard.domain.common.entity.CommonCodeDetail;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommonCodeDetailResponse {
    private Long id;
    private String typeCode;
    private String codeValue;
    private String codeName;
    private Integer sortOrder;
    private String isActive;

    public static CommonCodeDetailResponse from(CommonCodeDetail detail) {
        return new CommonCodeDetailResponse(
                detail.getId(),
                detail.getCommonCode().getTypeCode(),
                detail.getCodeValue(),
                detail.getCodeName(),
                detail.getSortOrder(),
                detail.getIsActive()
        );
    }
}
