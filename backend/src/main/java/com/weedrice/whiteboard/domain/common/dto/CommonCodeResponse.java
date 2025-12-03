package com.weedrice.whiteboard.domain.common.dto;

import com.weedrice.whiteboard.domain.common.entity.CommonCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CommonCodeResponse {
    private String typeCode;
    private String typeName;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static CommonCodeResponse from(CommonCode commonCode) {
        return new CommonCodeResponse(
                commonCode.getTypeCode(),
                commonCode.getTypeName(),
                commonCode.getDescription(),
                commonCode.getCreatedAt(),
                commonCode.getModifiedAt()
        );
    }
}
