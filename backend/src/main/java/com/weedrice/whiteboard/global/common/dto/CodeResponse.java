package com.weedrice.whiteboard.global.common.dto;

import com.weedrice.whiteboard.global.common.entity.CommonCodeDetail;
import lombok.Getter;

@Getter
public class CodeResponse {
    private final String code;
    private final String name;
    private final int sortOrder;

    public CodeResponse(CommonCodeDetail detail) {
        this.code = detail.getCodeValue();
        this.name = detail.getCodeName();
        this.sortOrder = detail.getSortOrder();
    }
}
