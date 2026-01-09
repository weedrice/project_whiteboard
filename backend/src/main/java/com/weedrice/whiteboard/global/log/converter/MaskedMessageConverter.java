package com.weedrice.whiteboard.global.log.converter;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.weedrice.whiteboard.global.log.filter.SensitiveDataMaskingFilter;

/**
 * 로그 메시지에서 민감한 정보를 마스킹하는 Converter
 * 
 * logback-spring.xml에서 사용:
 * <pattern>%d{...} %msg%n</pattern> → <pattern>%d{...} %maskedMsg%n</pattern>
 */
public class MaskedMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String originalMessage = super.convert(event);
        return SensitiveDataMaskingFilter.maskSensitiveData(originalMessage);
    }
}
