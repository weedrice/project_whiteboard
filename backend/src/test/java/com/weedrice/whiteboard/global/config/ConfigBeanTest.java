package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "cloud.aws.credentials.access-key=test-access",
    "cloud.aws.credentials.secret-key=test-secret",
    "cloud.aws.region.static=us-east-1",
    "cloud.aws.s3.bucket=test-bucket",
    "cloud.aws.ses.credentials.access-key=test-ses-access",
    "cloud.aws.ses.credentials.secret-key=test-ses-secret",
    "jwt.secret=c2VjcmV0LWtleS1mb3ItdGVzdGluZy1wdXJwb3Nlcy1vbmx5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbg==",
    "jwt.expiration=3600000",
    "jwt.refresh-token.expiration=7200000",
    "file.upload-dir=uploads",
    "app.frontend-url=http://localhost:5173"
}, 
    classes = {S3Config.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class ConfigBeanTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("S3Client 빈 등록 확인")
    void s3ClientBeanExists() {
        // S3Client 빈이 등록되어 있는지 확인
        S3Client s3Client = applicationContext.getBean(S3Client.class);
        assertThat(s3Client).isNotNull();
    }
}
