package com.weedrice.whiteboard.global.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.post.dto.PostSummaryFields;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson은 Lombok이 만든 {@code isXxx()} getter에서 {@code is} 접두사를 떼므로,
 * {@code private boolean isNotice}는 어노테이션이 없으면 wire에서 {@code notice}가 된다.
 *
 * <p>이 저장소는 이미 그렇게 나간 필드들을 legacy 계약으로 확정했다. 문제는 그 목록이
 * 손으로 관리되는 탓에 새 DTO가 어느 규칙을 따라야 하는지 코드가 알려주지 않고, 어긋나도
 * 빌드가 통과한다는 점이었다. 프론트는 그 필드를 {@code undefined}로 읽어 조용히 falsy가 된다.
 *
 * <p>그래서 이 테스트는 모든 DTO를 훑어 {@code boolean isXxx} 필드가 다음 중 하나를
 * 만족하지 못하면 실패한다.
 *
 * <ul>
 *   <li>필드 또는 getter에 {@code @JsonProperty}가 붙어 있다 (wire 이름을 명시했다)</li>
 *   <li>아래 legacy 허용 목록에 등재되어 있다 (기존 wire 이름을 유지하기로 한 필드다)</li>
 * </ul>
 *
 * <p>record와 Lombok 빌더는 제외한다. record는 Jackson이 컴포넌트 이름을 그대로 쓰므로
 * 접두사가 떨어지지 않고(같은 클래스의 테스트로 고정했다), 빌더는 직렬화 대상이 아니다.
 *
 * <p>새 필드를 목록에 추가하려면 그것이 정말 legacy 호환 때문인지 먼저 따져야 한다.
 * 신규 필드라면 {@code @JsonProperty}를 붙이는 쪽이 맞다.
 */
class BooleanWireNameContractTest {

    private static final String BASE_PACKAGE = "com/weedrice/whiteboard";

    /**
     * {@code is} 접두사가 떨어진 채로 이미 배포된 필드들. 값은 {@code 클래스단순명#필드명}이다.
     * 실제 wire 이름은 {@code ApiWireContractSerializationTest}가 따로 고정한다.
     *
     * <p><b>이 목록은 늘리지 않는 것이 원칙이다.</b> 항목을 지우려면 프론트엔드 정규화
     * 계층과 {@code backend/API명세서.md}의 표를 함께 고쳐야 한다.
     */
    private static final Set<String> LEGACY_UNPREFIXED_FIELDS = Set.of(
            "PostSummary#isNotice",
            "PostSummary#isNsfw",
            "PostSummary#isLiked",
            "PostSummary#isScrapped",
            "PostSummary#isSubscribed",
            "AdminResponse#isActive",
            "SuperAdminResponse#isSuperAdmin",
            "SuperAdminUpdateResponse#isSuperAdmin",
            "FeedSummary#isRead",
            "AgentPostLikeResponse#isLiked");

    @Test
    @DisplayName("DTO의 boolean isXxx 필드는 wire 이름을 명시하거나 legacy 목록에 등재되어야 한다")
    void everyPrefixedBooleanFieldDeclaresItsWireName() throws IOException {
        List<String> unguarded = new ArrayList<>();

        for (Class<?> dtoClass : findDtoClasses()) {
            for (Field field : dtoClass.getDeclaredFields()) {
                if (!isPrefixedBooleanField(field)) {
                    continue;
                }
                String key = dtoClass.getSimpleName() + "#" + field.getName();
                if (declaresWireName(dtoClass, field) || LEGACY_UNPREFIXED_FIELDS.contains(key)) {
                    continue;
                }
                unguarded.add(key + " (" + dtoClass.getName() + ")");
            }
        }

        assertThat(unguarded)
                .as("wire 이름이 정해지지 않은 boolean 필드가 있다. @JsonProperty로 이름을 명시하거나, "
                        + "기존 계약을 유지해야 한다면 LEGACY_UNPREFIXED_FIELDS에 등재할 것")
                .isEmpty();
    }

    @Test
    @DisplayName("legacy 허용 목록에 사라진 필드가 남아 있지 않다")
    void legacyListHasNoStaleEntries() throws IOException {
        Set<String> present = new LinkedHashSet<>();
        for (Class<?> dtoClass : findDtoClasses()) {
            for (Field field : dtoClass.getDeclaredFields()) {
                if (isPrefixedBooleanField(field)) {
                    present.add(dtoClass.getSimpleName() + "#" + field.getName());
                }
            }
        }

        assertThat(present)
                .as("legacy 목록의 항목이 실제 DTO에 없다. 필드가 사라졌다면 목록에서도 지울 것")
                .containsAll(LEGACY_UNPREFIXED_FIELDS);
    }

    private static boolean isPrefixedBooleanField(Field field) {
        return field.getType() == boolean.class
                && !Modifier.isStatic(field.getModifiers())
                && field.getName().matches("^is[A-Z].*");
    }

    /** 필드나 그 getter에 {@code @JsonProperty}가 있으면 wire 이름이 명시된 것으로 본다. */
    private static boolean declaresWireName(Class<?> owner, Field field) {
        if (field.isAnnotationPresent(JsonProperty.class)) {
            return true;
        }

        String capitalized = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (String getterName : List.of(field.getName(), "get" + capitalized)) {
            Method getter = findMethod(owner, getterName);
            if (getter != null && getter.isAnnotationPresent(JsonProperty.class)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("record 컴포넌트는 is 접두사가 유지되므로 검사 대상이 아니다")
    void recordComponentsKeepTheirPrefix() {
        JsonNode json = JsonMapper.builder().build()
                .valueToTree(new PostSummaryFields.Flags(true, false, true, false));

        // Jackson은 record를 bean getter가 아니라 컴포넌트 이름으로 직렬화한다.
        // 이 전제가 깨지면 record도 스캔 대상에 넣어야 하므로 여기서 고정한다.
        assertThat(json.has("isNotice")).isTrue();
        assertThat(json.has("notice")).isFalse();
    }

    /**
     * Lombok {@code @Builder}가 만드는 중첩 빌더 클래스는 응답으로 직렬화되지 않으므로 제외한다.
     * 빌더는 대상 DTO의 필드를 그대로 복제해 갖고 있어, 걸러내지 않으면 같은 필드가 두 번 걸린다.
     */
    private static boolean isGeneratedBuilder(Class<?> candidate) {
        return candidate.getEnclosingClass() != null && candidate.getSimpleName().endsWith("Builder");
    }

    private static Method findMethod(Class<?> owner, String name) {
        try {
            return owner.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** {@code dto} 패키지 아래의 모든 클래스(중첩 클래스 포함)를 찾는다. */
    private static List<Class<?>> findDtoClasses() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
        Resource[] resources = resolver.getResources("classpath*:" + BASE_PACKAGE + "/**/dto/**/*.class");

        List<Class<?>> classes = new ArrayList<>();
        for (Resource resource : resources) {
            String className = metadataReaderFactory.getMetadataReader(resource).getClassMetadata().getClassName();
            try {
                classes.add(Class.forName(className, false, BooleanWireNameContractTest.class.getClassLoader()));
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // 로드할 수 없는 클래스는 직렬화 대상도 아니므로 건너뛴다.
            }
        }

        classes.removeIf(candidate -> isGeneratedBuilder(candidate) || candidate.isRecord());

        assertThat(classes).as("DTO 클래스를 하나도 찾지 못했다. 스캔 경로가 바뀌었는지 확인할 것").isNotEmpty();
        return classes;
    }
}
