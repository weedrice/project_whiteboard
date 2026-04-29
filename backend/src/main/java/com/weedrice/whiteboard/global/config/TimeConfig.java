package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(DateTimeUtils.KST_ZONE_ID);
    }
}
