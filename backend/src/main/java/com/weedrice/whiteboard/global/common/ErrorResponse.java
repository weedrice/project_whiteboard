package com.weedrice.whiteboard.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public class ErrorResponse {
    private String code;
    private String message;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object details; // Validation 에러 등의 상세 정보
    
    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.details = null;
    }
    
    public ErrorResponse(String code, String message, Object details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }
}