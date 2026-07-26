package com.weedrice.whiteboard.global.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.post.dto.PostSummaryFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
 * {@code boolean isXxx} 필드가 어떤 이름으로 wire에 나가는지를 강제한다.
 *
 * <p>Jackson은 Lombok이 만든 {@code isXxx()} getter에서 {@code is}를 떼므로 기본 wire 이름은
 * {@code xxx}가 된다. 중요한 것은 <b>어노테이션을 어디에 붙이느냐에 따라 결과가 다르다</b>는 점이다.
 * 아래는 실제 직렬화 결과로 확인한 것이며 {@code wireNamingRulesHold}가 고정한다.
 *
 * <table border="1">
 *   <caption>어노테이션 위치별 wire 이름</caption>
 *   <tr><th>패턴</th><th>wire 이름</th></tr>
 *   <tr><th>패턴</th><th>wire 이름</th><th>역직렬화</th></tr>
 *   <tr><td>어노테이션 없음</td><td>{@code xxx} 하나</td><td>됨</td></tr>
 *   <tr><td>필드에만</td><td>{@code xxx}와 {@code isXxx} <b>둘 다</b></td><td>됨</td></tr>
 *   <tr><td>getter에만</td><td>{@code isXxx} 하나</td><td><b>안 됨(읽기 전용)</b></td></tr>
 *   <tr><td><b>필드와 getter 양쪽</b></td><td>{@code isXxx} 하나</td><td>됨</td></tr>
 * </table>
 *
 * <p>필드에만 붙이면 이름이 바뀌는 것이 아니라 <b>키가 하나 더 생긴다.</b> getter 파생 속성
 * {@code xxx}는 그대로 남고 필드가 별도 속성으로 추가되기 때문이다.
 *
 * <p>getter에만 붙이면 키는 하나가 되지만 <b>속성이 읽기 전용이 된다.</b> Lombok이 primitive
 * {@code boolean isXxx}에 만드는 getter는 {@code isXxx()}이고 Jackson이 유추하는 이름은
 * {@code is}가 떨어진 {@code xxx}인데, 필드의 유추 이름은 {@code isXxx}라 서로 다른 속성으로
 * 잡히기 때문이다. getter 쪽만 명시 이름을 받으면 그 속성에 mutator가 없다.
 * A8 1차 작업이 이 함정에 빠져 요청 본문의 {@code isSecret}이 무시됐다.
 *
 * <p>따라서 정답은 <b>필드와 getter 양쪽</b>에 같은 이름을 주는 것이다. 그래야 하나의 속성으로
 * 합쳐져 키도 하나이고 읽기·쓰기가 모두 된다. 역직렬화 쪽은
 * {@link BooleanWireRoundTripContractTest}가 고정한다.
 *
 * <p>필드 어노테이션 패턴은 A8에서 51개를 정리해 운영 코드에서 사라졌고,
 * {@code noProductionDtoUsesTheFieldAnnotationPattern}이 재유입을 막는다. 신규 DTO는 getter
 * 패턴을 쓰거나, 접두사가 떨어진 기존 계약을 유지해야 한다면 {@link #LEGACY_PREFIX_STRIPPED}에
 * 등재해야 한다. record와 Lombok 빌더는 제외한다. record는 컴포넌트 이름이 그대로 쓰여 접두사가 유지되고,
 * 빌더는 직렬화 대상이 아니다.
 *
 * <p>{@code agent}·{@code ad} 도메인도 제외한다. 자세한 이유는 {@link #EXCLUDED_DOMAIN_PACKAGES}.
 */
class BooleanWireNameContractTest {

    private static final String MAIN_CLASSES_MARKER = "/classes/java/main/";
    private static final String DTO_RESOURCE_PATTERN =
            "classpath*:com/weedrice/whiteboard/**/dto/**/*.class";

    /**
     * 이 계약에서 제외하는 도메인. 검사 대상에 넣으면 해당 도메인의 신규 DTO가 여기의
     * 규칙을 따르도록 강제되므로, 소스를 고치지 않아도 사실상 그 도메인을 건드리게 된다.
     * 소유자가 따로 있는 영역이라 wire 이름 결정도 그쪽에 맡긴다.
     */
    private static final Set<String> EXCLUDED_DOMAIN_PACKAGES = Set.of(
            "com.weedrice.whiteboard.domain.agent.",
            "com.weedrice.whiteboard.domain.ad.");

    /**
     * 스캔이 조용히 반쪽만 돌지 않았는지 보는 하한.
     * 제외 도메인과 빌더·record를 걸러낸 뒤 현재 217개다(2026-07-25 실측).
     */
    private static final int MINIMUM_EXPECTED_DTO_CLASSES = 100;

    /**
     * 어노테이션이 없어 {@code is}가 떨어진 채 배포된 필드. wire 이름은 {@code xxx} 하나다.
     * 키는 정규화된 클래스 이름을 쓴다. 단순 이름은 {@code UserInfo}처럼 여러 곳에 있어 충돌한다.
     */
    private static final Set<String> LEGACY_PREFIX_STRIPPED = Set.of(
            "com.weedrice.whiteboard.domain.post.dto.PostSummary#isNotice",
            "com.weedrice.whiteboard.domain.post.dto.PostSummary#isNsfw",
            "com.weedrice.whiteboard.domain.post.dto.PostSummary#isLiked",
            "com.weedrice.whiteboard.domain.post.dto.PostSummary#isScrapped",
            "com.weedrice.whiteboard.domain.post.dto.PostSummary#isSubscribed",
            "com.weedrice.whiteboard.domain.admin.dto.AdminResponse#isActive",
            "com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse#isSuperAdmin",
            "com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse#isSuperAdmin",
            "com.weedrice.whiteboard.domain.feed.dto.FeedResponse$FeedSummary#isRead");

    @Test
    @DisplayName("boolean isXxx 필드는 wire 이름 규칙 중 하나를 명시적으로 따라야 한다")
    void everyPrefixedBooleanFieldFollowsAKnownRule() throws IOException {
        List<String> unguarded = new ArrayList<>();

        for (Class<?> dtoClass : findDtoClasses()) {
            for (Field field : dtoClass.getDeclaredFields()) {
                if (!isPrefixedBooleanField(field)) {
                    continue;
                }
                String key = dtoClass.getName() + "#" + field.getName();
                if (annotatedOnBothSides(dtoClass, field) || LEGACY_PREFIX_STRIPPED.contains(key)) {
                    continue;
                }
                unguarded.add(key);
            }
        }

        assertThat(unguarded)
                .as("wire 이름이 정해지지 않은 boolean 필드가 있다. 필드와 getter 양쪽에 "
                        + "@JsonProperty(\"isXxx\")를 붙일 것"
                        + "(@JsonProperty + @Getter(onMethod_ = @JsonProperty(...))). "
                        + "필드에만 붙이면 키가 하나 더 생기고, getter에만 붙이면 읽기 전용이 되어 "
                        + "요청 본문의 값이 무시된다. 기존 계약을 유지해야 한다면 legacy 목록에 등재할 것")
                .isEmpty();
    }

    @Test
    @DisplayName("legacy 목록에 사라진 필드가 남아 있지 않다")
    void legacyListsHaveNoStaleEntries() throws IOException {
        Set<String> present = new LinkedHashSet<>();
        for (Class<?> dtoClass : findDtoClasses()) {
            for (Field field : dtoClass.getDeclaredFields()) {
                if (isPrefixedBooleanField(field)) {
                    present.add(dtoClass.getName() + "#" + field.getName());
                }
            }
        }

        assertThat(present)
                .as("legacy 목록의 항목이 실제 DTO에 없다. 필드가 사라졌다면 목록에서도 지울 것")
                .containsAll(LEGACY_PREFIX_STRIPPED);
    }

    @Test
    @DisplayName("제외 도메인은 스캔에도 legacy 목록에도 들어오지 않는다")
    void excludedDomainsStayOutOfTheContract() throws IOException {
        // 필터를 거치기 전에 실제로 제외 대상이 존재하는지부터 본다. 이 확인이 없으면
        // 접두사에 오타가 나면(`domain.agents.` 등) 술어가 모두 거짓이 되어 아래 검사가
        // 공허하게 통과한다. 즉 제외가 조용히 풀린 것을 잡지 못한다.
        List<String> allNames = findDtoClassNames();
        for (String prefix : EXCLUDED_DOMAIN_PACKAGES) {
            assertThat(allNames)
                    .as("접두사 '%s'에 걸리는 DTO가 스캔 경로에 하나도 없다. 접두사 오타이거나, "
                            + "해당 도메인이 사라졌다면 EXCLUDED_DOMAIN_PACKAGES에서도 지울 것", prefix)
                    .anyMatch(name -> name.startsWith(prefix));
        }

        assertThat(findDtoClasses())
                .as("제외 도메인의 DTO가 스캔에 들어왔다. 들어오면 그 도메인의 신규 필드가 "
                        + "이 계약을 따르도록 강제된다")
                .noneMatch(dtoClass -> isExcludedDomain(dtoClass.getName()));

        List<String> leaked = new ArrayList<>();
        for (String key : List.copyOf(LEGACY_PREFIX_STRIPPED)) {
            if (isExcludedDomain(key)) {
                leaked.add(key);
            }
        }
        assertThat(leaked)
                .as("제외 도메인의 필드가 legacy 목록에 남아 있다. 스캔에서 빠지므로 "
                        + "legacyListsHaveNoStaleEntries가 항상 실패한다")
                .isEmpty();
    }

    /**
     * 세 패턴의 결과를 고정하는 fixture.
     *
     * <p>필드 어노테이션 패턴은 이제 어느 운영 DTO에도 없다(A8 정리 완료). 그래도 규칙 자체는
     * 남겨 두어야 다음 사람이 같은 실수를 반복하지 않으므로, 운영 DTO 대신 여기서 재현한다.
     * {@code noProductionDtoUsesTheFieldAnnotationPattern}이 운영 코드에 다시 새어 나오지
     * 않도록 막는다.
     */
    @SuppressWarnings("unused")
    static class WireNamingFixture {
        private final boolean isUnannotated;

        @JsonProperty("isFieldAnnotated")
        private final boolean isFieldAnnotated;

        private final boolean isGetterAnnotated;

        WireNamingFixture(boolean value) {
            this.isUnannotated = value;
            this.isFieldAnnotated = value;
            this.isGetterAnnotated = value;
        }

        // Lombok이 만드는 getter와 같은 모양이다. 테스트 소스에는 Lombok이 없어 직접 쓴다.
        public boolean isUnannotated() {
            return isUnannotated;
        }

        public boolean isFieldAnnotated() {
            return isFieldAnnotated;
        }

        @JsonProperty("isGetterAnnotated")
        public boolean isGetterAnnotated() {
            return isGetterAnnotated;
        }
    }

    @Test
    @DisplayName("어노테이션 위치별 wire 이름 규칙을 실제 직렬화로 고정한다")
    void wireNamingRulesHold() {
        JsonNode json = JsonMapper.builder().build().valueToTree(new WireNamingFixture(true));

        // 어노테이션 없음 -> is가 떨어진 이름 하나
        assertThat(json.has("unannotated")).isTrue();
        assertThat(json.has("isUnannotated")).isFalse();

        // 필드에 @JsonProperty -> 이름이 바뀌는 것이 아니라 두 이름이 함께 나간다
        assertThat(json.has("fieldAnnotated")).isTrue();
        assertThat(json.has("isFieldAnnotated")).isTrue();

        // getter에 @JsonProperty -> is가 붙은 이름 하나
        assertThat(json.has("isGetterAnnotated")).isTrue();
        assertThat(json.has("getterAnnotated")).isFalse();
    }

    @Test
    @DisplayName("운영 DTO는 한쪽에만 @JsonProperty를 붙이지 않는다")
    void noProductionDtoAnnotatesOnlyOneSide() throws IOException {
        List<String> lopsided = new ArrayList<>();

        for (Class<?> dtoClass : findDtoClasses()) {
            for (Field field : dtoClass.getDeclaredFields()) {
                if (!isPrefixedBooleanField(field)) {
                    continue;
                }
                String key = dtoClass.getName() + "#" + field.getName();
                if (LEGACY_PREFIX_STRIPPED.contains(key)) {
                    continue;
                }
                boolean hasAny = field.isAnnotationPresent(JsonProperty.class)
                        || annotatedOnGetterRegardlessOfName(dtoClass, field);
                if (hasAny && !annotatedOnBothSides(dtoClass, field)) {
                    lopsided.add(key);
                }
            }
        }

        assertThat(lopsided)
                .as("한쪽에만 @JsonProperty가 붙은 boolean 필드가 있다. "
                        + "필드에만 붙이면 키가 하나 더 나가고, getter에만 붙이면 읽기 전용이 되어 "
                        + "요청 본문의 값이 조용히 무시된다. 양쪽에 같은 이름을 붙일 것")
                .isEmpty();
    }

    @Test
    @DisplayName("A8에서 정리한 DTO는 키를 하나만 내보낸다")
    void cleanedDtosEmitASingleKey() {
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode summary = mapper.valueToTree(
                com.weedrice.whiteboard.domain.post.dto.PostSummary.builder()
                        .isBlinded(true)
                        .isSecret(true)
                        .isSpoiler(true)
                        .build());
        assertThat(summary.has("isBlinded")).isTrue();
        assertThat(summary.has("blinded")).isFalse();
        assertThat(summary.has("isSecret")).isTrue();
        assertThat(summary.has("secret")).isFalse();

        JsonNode post = mapper.valueToTree(
                com.weedrice.whiteboard.domain.post.dto.PostResponse.builder()
                        .isNotice(true)
                        .isLiked(true)
                        .build());
        assertThat(post.has("isNotice")).isTrue();
        assertThat(post.has("notice")).isFalse();
        assertThat(post.has("isLiked")).isTrue();
        assertThat(post.has("liked")).isFalse();

        JsonNode comment = mapper.valueToTree(
                com.weedrice.whiteboard.domain.comment.dto.CommentResponse.builder()
                        .isDeleted(true)
                        .build());
        assertThat(comment.has("isDeleted")).isTrue();
        assertThat(comment.has("deleted")).isFalse();

        // 어노테이션이 없어 접두사가 떨어진 필드는 이번 정리 대상이 아니다. 그대로여야 한다.
        assertThat(summary.has("notice")).isTrue();
        assertThat(summary.has("isNotice")).isFalse();
    }

    @Test
    @DisplayName("record 컴포넌트는 is 접두사가 유지되므로 검사 대상이 아니다")
    void recordComponentsKeepTheirPrefix() {
        JsonNode json = JsonMapper.builder().build()
                .valueToTree(new PostSummaryFields.Flags(true, false, true, false));

        assertThat(json.has("isNotice")).isTrue();
        assertThat(json.has("notice")).isFalse();
    }

    private static boolean isPrefixedBooleanField(Field field) {
        return field.getType() == boolean.class
                && !Modifier.isStatic(field.getModifiers())
                && field.getName().matches("^is[A-Z].*");
    }

    /**
     * 필드와 getter <b>양쪽</b>에 같은 이름의 {@code @JsonProperty}가 있어야 한 속성으로 합쳐져
     * 키가 하나가 되고 읽기·쓰기가 모두 된다. 한쪽만 있으면 키가 늘거나 읽기 전용이 된다.
     */
    private static boolean annotatedOnBothSides(Class<?> owner, Field field) {
        JsonProperty onField = field.getAnnotation(JsonProperty.class);
        if (onField == null) {
            return false;
        }

        String capitalized = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (String getterName : List.of(field.getName(), "get" + capitalized)) {
            Method getter = findMethod(owner, getterName);
            if (getter == null) {
                continue;
            }
            JsonProperty onGetter = getter.getAnnotation(JsonProperty.class);
            // 이름까지 같아야 한다. 다르면 속성이 둘로 갈려 키가 두 개 나간다.
            if (onGetter != null && onGetter.value().equals(onField.value())) {
                return true;
            }
        }
        return false;
    }

    /** 이름이 무엇이든 getter에 어노테이션이 있는지만 본다. 한쪽만 붙은 상태를 찾는 데 쓴다. */
    private static boolean annotatedOnGetterRegardlessOfName(Class<?> owner, Field field) {
        String capitalized = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (String getterName : List.of(field.getName(), "get" + capitalized)) {
            Method getter = findMethod(owner, getterName);
            if (getter != null && getter.isAnnotationPresent(JsonProperty.class)) {
                return true;
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> owner, String name) {
        try {
            return owner.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * {@code dto} 패키지 아래 <b>main</b> 클래스만 모은다. 테스트 클래스까지 훑으면
     * 이 테스트 자신이 걸려 "하나도 못 찾았다" 검사가 항상 통과해 버린다.
     */
    private static List<Class<?>> findDtoClasses() throws IOException {
        List<Class<?>> classes = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String className : findDtoClassNames()) {
            if (isExcludedDomain(className)) {
                continue;
            }
            try {
                classes.add(Class.forName(className, false, BooleanWireNameContractTest.class.getClassLoader()));
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                skipped.add(className + " (" + e.getClass().getSimpleName() + ")");
            }
        }

        classes.removeIf(candidate -> isGeneratedBuilder(candidate) || candidate.isRecord());

        assertThat(skipped).as("로드할 수 없는 DTO는 검사에서 빠지므로 원인을 확인할 것").isEmpty();
        assertThat(classes)
                .as("스캔이 반쪽만 돌았을 수 있다. 컴파일 산출물이 최신인지 확인할 것")
                .hasSizeGreaterThanOrEqualTo(MINIMUM_EXPECTED_DTO_CLASSES);
        return classes;
    }

    private static boolean isExcludedDomain(String className) {
        return EXCLUDED_DOMAIN_PACKAGES.stream().anyMatch(className::startsWith);
    }

    /** 제외 필터를 거치기 <b>전</b>의 이름 전체. 제외가 실제로 무언가를 걸러내는지 확인하는 데 쓴다. */
    private static List<String> findDtoClassNames() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);

        List<String> names = new ArrayList<>();
        for (Resource resource : resolver.getResources(DTO_RESOURCE_PATTERN)) {
            if (!resource.getURL().toString().contains(MAIN_CLASSES_MARKER)) {
                continue;
            }
            names.add(metadataReaderFactory.getMetadataReader(resource).getClassMetadata().getClassName());
        }
        return names;
    }

    /**
     * Lombok {@code @Builder}가 만드는 중첩 빌더는 직렬화 대상이 아니다.
     * 빌더는 대상 DTO의 필드를 복제해 갖고 있어 걸러내지 않으면 같은 필드가 두 번 걸린다.
     */
    private static boolean isGeneratedBuilder(Class<?> candidate) {
        Class<?> enclosing = candidate.getEnclosingClass();
        return enclosing != null && candidate.getSimpleName().equals(enclosing.getSimpleName() + "Builder");
    }
}
