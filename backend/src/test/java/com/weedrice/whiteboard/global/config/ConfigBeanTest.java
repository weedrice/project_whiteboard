package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
import com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor;
import com.weedrice.whiteboard.global.security.RefererCheckInterceptor;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "cloud.aws.credentials.access-key=test-access",
    "cloud.aws.credentials.secret-key=test-secret",
    "cloud.aws.region.static=us-east-1",
    "cloud.aws.s3.bucket=test-bucket",
    "cloud.aws.ses.credentials.access-key=test-ses-access",
    "cloud.aws.ses.credentials.secret-key=test-ses-secret",
    "cloud.aws.ses.sender=test@example.com",
    "jwt.secret=c2VjcmV0LWtleS1mb3ItdGVzdGluZy1wdXJwb3Nlcy1vbmx5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbg==",
    "jwt.expiration=3600000",
    "jwt.refresh-token.expiration=7200000",
    "file.upload-dir=uploads",
    "app.frontend-url=http://localhost:5173"
}, 
    classes = {
        S3Config.class, WebConfig.class, AsyncConfig.class, 
        CacheConfig.class, MessageConfig.class, SesConfig.class, 
        OpenApiConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class ConfigBeanTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private IpBlockInterceptor ipBlockInterceptor;

    @MockBean
    private RefererCheckInterceptor refererCheckInterceptor;

    @MockBean
    private RateLimitInterceptor rateLimitInterceptor;

    @Test
    @DisplayName("S3Client 빈 등록 확인")
    void s3ClientBeanExists() {
        S3Client s3Client = applicationContext.getBean(S3Client.class);
        assertThat(s3Client).isNotNull();
    }

    @Test
    @DisplayName("SesClient 빈 등록 확인")
    void sesClientBeanExists() {
        SesClient sesClient = applicationContext.getBean(SesClient.class);
        assertThat(sesClient).isNotNull();
    }

    @Test
    @DisplayName("WebMvcConfigurer 빈 등록 확인")
    void webMvcConfigurerBeanExists() {
        WebMvcConfigurer webMvcConfigurer = applicationContext.getBean(WebMvcConfigurer.class);
        assertThat(webMvcConfigurer).isNotNull();
    }

    @Test
    @DisplayName("Async Executor 빈 등록 확인")
    void asyncExecutorBeanExists() {
        Executor executor = applicationContext.getBean("taskExecutor", Executor.class);
        assertThat(executor).isNotNull();
    }

    @Test
    @DisplayName("CacheManager 빈 등록 확인")
    void cacheManagerBeanExists() {
        CacheManager cacheManager = applicationContext.getBean(CacheManager.class);
        assertThat(cacheManager).isNotNull();
    }

    @Test
    @DisplayName("MessageSource 및 Validator 빈 등록 확인")
    void messageSourceBeanExists() {
        MessageSource messageSource = applicationContext.getBean(MessageSource.class);
        LocalValidatorFactoryBean validator = applicationContext.getBean(LocalValidatorFactoryBean.class);
        
        assertThat(messageSource).isNotNull();
        assertThat(validator).isNotNull();
    }

    @Test
    @DisplayName("OpenAPI 빈 등록 확인")
    void openApiBeanExists() {
        OpenAPI openAPI = applicationContext.getBean(OpenAPI.class);
        assertThat(openAPI).isNotNull();
    }
}
