package com.weedrice.whiteboard.global.common.annotation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 공통 API 응답 어노테이션
 * 
 * 컨트롤러 메서드에 공통 에러 응답을 추가합니다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "400", description = "잘못된 요청")
@ApiResponse(responseCode = "401", description = "인증 실패")
@ApiResponse(responseCode = "403", description = "권한 없음")
@ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없음")
@ApiResponse(responseCode = "500", description = "서버 내부 오류")
public @interface ApiCommonResponses {
}
