package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 프론트 타입 생성(A6)의 입력이 되는 OpenAPI 스냅샷을 만들고 검증한다.
 *
 * <p>서버를 띄우지 않고 MockMvc로 springdoc 엔드포인트를 호출하므로 CI에서 그대로 돈다.
 * 스냅샷을 저장소에 커밋해 두는 이유는 두 가지다.
 * <ol>
 *   <li>프론트 타입 생성이 실행 중인 백엔드에 의존하지 않는다.</li>
 *   <li>스펙이 바뀌면 diff로 드러난다. 생성 타입만 커밋하면 무엇이 왜 바뀌었는지 알 수 없다.</li>
 * </ol>
 *
 * <p>스펙이 바뀌었는데 스냅샷을 갱신하지 않으면 이 테스트가 실패한다. 갱신 방법:
 * {@code UPDATE_OPENAPI_SNAPSHOT=true ./gradlew test --tests '*OpenApiSpecSnapshotTest*'}
 */
// 전체 컨텍스트가 필요하다. springdoc은 등록된 핸들러를 훑어 스펙을 만들므로
// 슬라이스로는 일부 경로만 담긴 스펙이 나와 스냅샷이 의미를 잃는다.
// 자격 증명은 ConfigBeanTest와 같은 더미 값을 쓴다. 실제 AWS 호출은 없다.
@SpringBootTest(properties = {
    // application-postgres-smoke.yml이 전체 컨텍스트를 띄울 때 쓰는 값과 같다.
    // 새 application-*.yml을 만들지 않으려고 여기에 인라인했다. 전부 더미이고
    // 외부 호출은 일어나지 않는다.
    "cloud.aws.credentials.access-key=snapshot-access-key",
    "cloud.aws.credentials.secret-key=snapshot-secret-key",
    "cloud.aws.region.static=us-east-1",
    "cloud.aws.s3.bucket=snapshot-bucket",
    "cloud.aws.password-reset.frontend-url=http://localhost:5173/reset-password#token=",
    "jwt.secret=c2VjcmV0LWtleS1mb3ItdGVzdGluZy1wdXJwb3Nlcy1vbmx5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbg==",
    "jwt.expiration=1800000",
    "jwt.refresh-token.expiration=1209600000",
    "file.upload-dir=uploads",
    "app.frontend-url=http://localhost:5173",
    "app.scheduling.enabled=false",
    // spring.mail.host가 있어야 Boot가 JavaMailSender를 만든다.
    "spring.mail.host=localhost",
    "spring.mail.port=1025",
    "spring.mail.username=snapshot@example.com",
    "spring.mail.password=snapshot-password",
    // src/test/resources/application.yml이 main의 것을 통째로 가린다. 운영에서 쓰는
    // 경로를 명시하지 않으면 springdoc 기본값(/v3/api-docs)으로 떨어져 404가 난다.
    "springdoc.api-docs.enabled=true",
    "springdoc.api-docs.path=/api-docs"
})@ActiveProfiles("test")
class OpenApiSpecSnapshotTest {

    /** 저장소 루트 기준 경로. 프론트 생성 스크립트가 같은 파일을 읽는다. */
    private static final Path SNAPSHOT = Path.of("../docs/api/openapi-frontend.json");

    private static final String UPDATE_FLAG = "UPDATE_OPENAPI_SNAPSHOT";

    private static final String CODEGEN_SPEC_PATH = "/api-docs/" + OpenApiConfig.FRONTEND_CODEGEN_GROUP;
    private static final String FULL_SPEC_PATH = "/api-docs";

    @Autowired
    private WebApplicationContext context;

    @Test
    @DisplayName("프론트 codegen 스펙 스냅샷이 최신이다")
    void snapshotIsUpToDate() throws Exception {
        String actual = normalizedSpec(CODEGEN_SPEC_PATH);

        if (Boolean.parseBoolean(System.getenv(UPDATE_FLAG))) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(SNAPSHOT))
                .as("스냅샷이 없다. %s=true 로 한 번 생성할 것", UPDATE_FLAG)
                .isTrue();
        assertThat(actual)
                .as("OpenAPI 스펙이 스냅샷과 다르다. 의도한 변경이면 %s=true 로 갱신하고 "
                        + "생성 타입(npm run api:generate)도 함께 커밋할 것", UPDATE_FLAG)
                .isEqualTo(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("codegen 스펙에 제외 도메인 경로가 들어 있지 않다")
    void excludedDomainsAreAbsentFromTheCodegenSpec() throws Exception {
        JsonNode paths = readPaths(CODEGEN_SPEC_PATH);

        assertThat(paths.isObject() && !paths.isEmpty())
                .as("스펙에 경로가 하나도 없다. 그룹 이름이나 필터가 잘못되면 빈 스펙이 나와 "
                        + "아래 검사가 공허하게 통과한다")
                .isTrue();

        assertThat(pathsUnderExcludedDomains(paths))
                .as("제외 도메인 경로가 생성 대상에 들어왔다. 들어오면 그 도메인의 DTO 변경이 "
                        + "프론트 빌드를 깨뜨린다")
                .isEmpty();
    }

    @Test
    @DisplayName("제외 필터가 실제로 무언가를 걸러낸다")
    void theExclusionActuallyRemovesSomething() throws Exception {
        // 전체 문서에는 제외 대상 경로가 있어야 한다. 없다면 위 검사가 통과한 이유가
        // 필터가 옳아서가 아니라 애초에 문서화되지 않아서일 수 있다.
        assertThat(pathsUnderExcludedDomains(readPaths(FULL_SPEC_PATH)))
                .as("전체 문서에도 제외 도메인 경로가 없다. 제외 검사가 공허해진다")
                .isNotEmpty();
    }

    private static List<String> pathsUnderExcludedDomains(JsonNode paths) {
        List<String> matched = new ArrayList<>();
        paths.propertyNames().forEach(path -> {
            if (path.startsWith("/api/v1/agents") || path.startsWith("/api/v1/ads")) {
                matched.add(path);
            }
        });
        return matched;
    }

    private JsonNode readPaths(String specPath) throws Exception {
        return JsonMapper.builder().build().readTree(normalizedSpec(specPath)).path("paths");
    }

    /** springdoc 응답을 안정적인 키 순서·들여쓰기로 정규화한다. 그래야 diff가 의미를 갖는다. */
    private String normalizedSpec(String specPath) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        MockHttpServletResponse response = mockMvc.perform(get(specPath)).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        // 오류 응답을 그대로 스냅샷에 쓰면, 스펙이 통째로 사라진 상태가 "정상"으로 굳는다.
        // 경로 오타나 springdoc 설정 실수가 조용히 통과하던 실제 사고를 여기서 막는다.
        assertThat(response.getStatus())
                .as("%s 가 200을 주지 않았다. 응답 본문: %s", specPath, body)
                .isEqualTo(200);
        assertThat(body)
                .as("%s 응답이 OpenAPI 문서가 아니다. 본문: %s", specPath, body)
                .contains("\"openapi\"")
                .contains("\"paths\"");

        ObjectMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        return mapper.writeValueAsString(mapper.readTree(body)) + "\n";
    }
}
