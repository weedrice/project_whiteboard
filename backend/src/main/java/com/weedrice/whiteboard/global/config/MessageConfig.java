package com.weedrice.whiteboard.global.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
public class MessageConfig {

    /**
     * 이 빈이 `messageSource`라는 이름으로 존재하므로 Spring Boot의
     * MessageSourceAutoConfiguration(@ConditionalOnMissingBean(name = "messageSource"))이
     * 물러난다. 즉 `spring.messages.*` 속성은 적용되지 않으므로 설정은 모두 여기에 둔다.
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        // 키가 없을 때 시스템 로케일 번들이 아니라 기본 번들(messages.properties)로 떨어지게 한다.
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Bean
    public LocalValidatorFactoryBean getValidator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource());
        return bean;
    }
}
