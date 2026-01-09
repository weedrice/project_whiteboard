package com.weedrice.whiteboard.global.util;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * 입력값 Sanitization 유틸리티
 * 
 * XSS 공격 방지를 위한 입력값 정제 기능을 제공합니다.
 */
public class InputSanitizer {

    // HTML 태그 제거를 위한 정규식
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    // 위험한 스크립트 태그 패턴
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<\\s*script[^>]*>.*?</\\s*script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL
    );

    // 위험한 이벤트 핸들러 패턴
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "on\\w+\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * HTML 태그를 이스케이프합니다.
     * 
     * @param input 입력 문자열
     * @return 이스케이프된 문자열
     */
    public static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(input);
    }

    /**
     * HTML 태그를 제거합니다.
     * 
     * @param input 입력 문자열
     * @return HTML 태그가 제거된 문자열
     */
    public static String stripHtml(String input) {
        if (input == null) {
            return null;
        }
        return HTML_TAG_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * 위험한 스크립트와 이벤트 핸들러를 제거합니다.
     * 
     * @param input 입력 문자열
     * @return 정제된 문자열
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input;
        
        // 스크립트 태그 제거
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        
        // 이벤트 핸들러 제거
        sanitized = EVENT_HANDLER_PATTERN.matcher(sanitized).replaceAll("");
        
        return sanitized;
    }

    /**
     * HTML 태그가 포함되어 있는지 확인합니다.
     * 
     * @param input 입력 문자열
     * @return HTML 태그가 포함되어 있으면 true
     */
    public static boolean containsHtml(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return HTML_TAG_PATTERN.matcher(input).find();
    }

    /**
     * 위험한 스크립트가 포함되어 있는지 확인합니다.
     * 
     * @param input 입력 문자열
     * @return 위험한 스크립트가 포함되어 있으면 true
     */
    public static boolean containsScript(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return SCRIPT_PATTERN.matcher(input).find() || 
               EVENT_HANDLER_PATTERN.matcher(input).find();
    }
}
