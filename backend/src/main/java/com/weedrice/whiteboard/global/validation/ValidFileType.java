package com.weedrice.whiteboard.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 파일 타입 검증 어노테이션
 * 
 * MultipartFile의 MIME 타입과 확장자를 검증합니다.
 */
@Documented
@Constraint(validatedBy = ValidFileTypeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFileType {
    String message() default "허용되지 않은 파일 형식입니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * 허용할 MIME 타입 목록
     * 예: {"image/jpeg", "image/png", "image/gif"}
     */
    String[] allowedMimeTypes() default {};
    
    /**
     * 허용할 파일 확장자 목록 (점 포함, 소문자)
     * 예: {".jpg", ".jpeg", ".png", ".gif"}
     */
    String[] allowedExtensions() default {};
    
    /**
     * 이미지 파일만 허용할지 여부
     */
    boolean imageOnly() default false;
    
    /**
     * 최대 파일 크기 (바이트 단위)
     * 기본값: 10MB (10 * 1024 * 1024)
     */
    long maxSize() default 10 * 1024 * 1024;
}
