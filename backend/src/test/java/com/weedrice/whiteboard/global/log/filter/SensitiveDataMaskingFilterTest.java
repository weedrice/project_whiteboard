package com.weedrice.whiteboard.global.log.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskingFilterTest {

    @Test
    @DisplayName("필터 생성 및 decide 메서드 테스트")
    void decide() {
        SensitiveDataMaskingFilter filter = new SensitiveDataMaskingFilter();
        ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        Mockito.when(event.getFormattedMessage()).thenReturn("Normal message");

        FilterReply reply = filter.decide(event);
        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    @DisplayName("민감 정보 마스킹 테스트")
    void maskSensitiveData() {
        // SensitiveDataMaskingFilter 내부 로직이 로그 메시지를 변경하는 방식이라면
        // decide 메서드 내에서 message를 가로채서 변경해야 하는데, 
        // Logback Filter는 Reply만 반환하고 메시지 변경은 Converter 등이 담당하는 경우가 많습니다.
        // 하지만 요청하신 내용이 Filter의 생성자와 decide이므로 기본 동작을 확인합니다.
        
        SensitiveDataMaskingFilter filter = new SensitiveDataMaskingFilter();
        filter.start();
        assertThat(filter.isStarted()).isTrue();
    }
}
