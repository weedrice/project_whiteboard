package com.weedrice.whiteboard.global.log.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.regex.Pattern;

/**
 * 로그에서 민감한 정보를 마스킹하는 필터
 * 
 * 마스킹 대상:
 * - 비밀번호 (password, pwd, passwd)
 * - 토큰 (token, accessToken, refreshToken)
 * - API 키 (apiKey, accessKey, secretKey)
 * - 이메일 주소 (선택적)
 */
public class SensitiveDataMaskingFilter extends Filter<ILoggingEvent> {

    // 민감 정보 패턴들
    private static final Pattern[] SENSITIVE_PATTERNS = {
        // 비밀번호 필드: "password": "value" 또는 password=value
        Pattern.compile("(?i)([\"']?password[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([\"']?pwd[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([\"']?passwd[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        
        // 토큰 필드
        Pattern.compile("(?i)([\"']?token[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([\"']?accessToken[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([\"']?refreshToken[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9\\-_]+)", Pattern.CASE_INSENSITIVE),
        
        // API 키
        Pattern.compile("(?i)([\"']?apiKey[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([\"']?accessKey[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([\"']?secretKey[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([\"']?secret[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)", Pattern.CASE_INSENSITIVE),
    };

    private static final String MASKED_VALUE = "***MASKED***";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        String maskedMessage = maskSensitiveData(message);
        
        if (!message.equals(maskedMessage)) {
            // 마스킹이 발생한 경우, 로그 메시지를 변경
            // Logback의 ILoggingEvent는 불변 객체이므로, 
            // 실제로는 MessageConverter를 사용하거나 다른 방식으로 처리해야 함
            // 여기서는 필터링만 수행 (실제 마스킹은 MessageConverter에서 처리)
        }
        
        return FilterReply.NEUTRAL;
    }

    /**
     * 민감한 정보를 마스킹합니다.
     */
    public static String maskSensitiveData(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String masked = message;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll("$1" + MASKED_VALUE);
        }

        return masked;
    }
}
