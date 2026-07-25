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
 * 아래는 이 저장소의 실제 직렬화 결과로 확인한 것이며 {@code wireNamingRulesHoldForRealDtos}가 고정한다.
 *
 * <table border="1">
 *   <caption>어노테이션 위치별 wire 이름</caption>
 *   <tr><th>패턴</th><th>wire 이름</th></tr>
 *   <tr><td>어노테이션 없음</td><td>{@code xxx} 하나</td></tr>
 *   <tr><td>필드에 {@code @JsonProperty}</td><td>{@code xxx}와 {@code isXxx} <b>둘 다</b></td></tr>
 *   <tr><td>getter에 {@code @JsonProperty}</td><td>{@code isXxx} 하나</td></tr>
 * </table>
 *
 * <p>필드에 붙이면 이름이 바뀌는 것이 아니라 <b>키가 하나 더 생긴다.</b> getter 파생 속성
 * {@code xxx}는 그대로 남고 필드가 별도 속성으로 추가되기 때문이다. 따라서 이름을 정하려면
 * {@code @Getter(onMethod_ = @JsonProperty("isXxx"))}나 명시적 getter를 써야 한다.
 *
 * <p>신규 DTO는 getter 패턴을 쓰거나, 기존 계약을 유지해야 한다면 아래 목록에 등재해야 한다.
 * record와 Lombok 빌더는 제외한다. record는 컴포넌트 이름이 그대로 쓰여 접두사가 유지되고,
 * 빌더는 직렬화 대상이 아니다.
 */
class BooleanWireNameContractTest {

    private static final String MAIN_CLASSES_MARKER = "/classes/java/main/";
    private static final String DTO_RESOURCE_PATTERN =
            "classpath*:com/weedrice/whiteboard/**/dto/**/*.class";

    /** 스캔이 조용히 반쪽만 돌지 않았는지 보는 하한. 현재 290개가 넘는다. */
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
            "com.weedrice.whiteboard.domain.feed.dto.FeedResponse$FeedSummary#isRead",
            "com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse#isLiked");

    /**
     * 필드에 {@code @JsonProperty}가 붙어 {@code xxx}와 {@code isXxx}가 <b>둘 다</b> 나가는 필드.
     * 프론트엔드가 두 이름을 섞어 읽고 있어 어느 쪽도 지금은 지울 수 없다.
     *
     * <p><b>이 목록은 늘리지 않는다.</b> 정리하려면 프론트엔드가 참조하는 이름을 하나로 모으고,
     * getter 패턴으로 옮긴 뒤 목록에서 지우는 순서로 진행해야 한다.
     */
    private static final Set<String> LEGACY_DUPLICATE_KEYS = Set.of(
            "com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem#isNotice",
            "com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem#isSecret",
            "com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse#isReregister",
            "com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse#isActive",
            "com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse#isPublic",
            "com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse#isAdmin",
            "com.weedrice.whiteboard.domain.board.dto.BoardListResponse#isActive",
            "com.weedrice.whiteboard.domain.board.dto.BoardListResponse#isPublic",
            "com.weedrice.whiteboard.domain.board.dto.BoardListResponse#isSubscribed",
            "com.weedrice.whiteboard.domain.board.dto.CategoryResponse#isDefault",
            "com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse#isActive",
            "com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse#isPublic",
            "com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse#isSubscribed",
            "com.weedrice.whiteboard.domain.comment.dto.CommentResponse#isBlinded",
            "com.weedrice.whiteboard.domain.comment.dto.CommentResponse#isBlockedAuthor",
            "com.weedrice.whiteboard.domain.comment.dto.CommentResponse#isDeleted",
            "com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary#isNotice",
            "com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary#isNsfw",
            "com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary#isSecret",
            "com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary#isSpoiler",
            "com.weedrice.whiteboard.domain.post.dto.DraftResponse#isNotice",
            "com.weedrice.whiteboard.domain.post.dto.DraftResponse#isNsfw",
            "com.weedrice.whiteboard.domain.post.dto.DraftResponse#isSecret",
            "com.weedrice.whiteboard.domain.post.dto.DraftResponse#isSpoiler",
            "com.weedrice.whiteboard.domain.post.dto.PostCreateRequest#isNotice",
            "com.weedrice.whiteboard.domain.post.dto.PostCreateRequest#isNsfw",
            "com.weedrice.whiteboard.domain.post.dto.PostCreateRequest#isSecret",
            "com.weedrice.whiteboard.domain.post.dto.PostCreateRequest#isSpoiler",
            "com.weedrice.whiteboard.domain.post.dto.PostDraftRequest#isNotice",
            "com.weedrice.whiteboard.domain.post.dto.PostDraftRequest#isNsfw",
            "com.weedrice.whiteboard.domain.post.dto.PostDraftRequest#isSecret",
            "com.weedrice.whiteboard.domain.post.dto.PostDraftRequest#isSpoiler",
            "com.weedrice.whiteboard.domain.post.dto.PostResponse#isBlinded",
            "com.weedrice.whiteboard.domain.post.dto.PostResponse#isLiked",
            "com.weedrice.whiteboard.domain.post.dto.PostResponse#isNotice",
            "com.weedrice.whiteboard.domain.post.dto.PostResponse#isNsfw",
            "com.weedrice.whiteboard.domain.post.dto.PostResponse#isScrapped",
            "com.weedrice.whiteboard.domain.post.dto.PostResponse#isSecret",
            "com.weedrice.whiteboard.domain.post.dto.PostResponse#isSpoiler",
            "com.weedrice.whiteboard.domain.post.dto.PostResponse$BoardInfo#isAdmin",
            "com.weedrice.whiteboard.domain.post.dto.PostSummary#isBlinded",
            "com.weedrice.whiteboard.domain.post.dto.PostSummary#isSecret",
            "com.weedrice.whiteboard.domain.post.dto.PostSummary#isSpoiler",
            "com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest#isNsfw",
            "com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest#isSecret",
            "com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest#isSpoiler",
            "com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest#isNotice",
            "com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest#isNsfw",
            "com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest#isSecret",
            "com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest#isSpoiler",
            "com.weedrice.whiteboard.domain.user.dto.NotificationSettingResponse#isEnabled");

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
                if (annotatedOnGetter(dtoClass, field)
                        || LEGACY_PREFIX_STRIPPED.contains(key)
                        || LEGACY_DUPLICATE_KEYS.contains(key)) {
                    continue;
                }
                unguarded.add(key);
            }
        }

        assertThat(unguarded)
                .as("wire 이름이 정해지지 않은 boolean 필드가 있다. "
                        + "@Getter(onMethod_ = @JsonProperty(\"isXxx\"))로 이름을 명시하거나, "
                        + "기존 계약을 유지해야 한다면 해당 legacy 목록에 등재할 것. "
                        + "필드에 직접 @JsonProperty를 붙이면 이름이 바뀌지 않고 키가 하나 더 생긴다")
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
                .containsAll(LEGACY_PREFIX_STRIPPED)
                .containsAll(LEGACY_DUPLICATE_KEYS);
    }

    @Test
    @DisplayName("어노테이션 위치별 wire 이름 규칙을 실제 직렬화로 고정한다")
    void wireNamingRulesHoldForRealDtos() {
        JsonMapper mapper = JsonMapper.builder().build();

        // 어노테이션 없음 -> is가 떨어진 이름 하나
        JsonNode summary = mapper.valueToTree(
                com.weedrice.whiteboard.domain.post.dto.PostSummary.builder()
                        .isNotice(true)
                        .isBlinded(true)
                        .build());
        assertThat(summary.has("notice")).isTrue();
        assertThat(summary.has("isNotice")).isFalse();

        // 필드에 @JsonProperty -> 두 이름이 함께 나간다
        assertThat(summary.has("blinded")).isTrue();
        assertThat(summary.has("isBlinded")).isTrue();

        // getter에 @JsonProperty -> is가 붙은 이름 하나
        JsonNode userInfo = mapper.valueToTree(
                com.weedrice.whiteboard.domain.auth.dto.LoginResponse.UserInfo.builder()
                        .isEmailVerified(true)
                        .build());
        assertThat(userInfo.has("isEmailVerified")).isTrue();
        assertThat(userInfo.has("emailVerified")).isFalse();
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
     * getter에 {@code @JsonProperty}가 있어야 wire 이름이 하나로 정해진다.
     * 필드 어노테이션은 이름을 정하지 못하고 키를 늘리므로 여기서 인정하지 않는다.
     */
    private static boolean annotatedOnGetter(Class<?> owner, Field field) {
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
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
        Resource[] resources = resolver.getResources(DTO_RESOURCE_PATTERN);

        List<Class<?>> classes = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Resource resource : resources) {
            if (!resource.getURL().toString().contains(MAIN_CLASSES_MARKER)) {
                continue;
            }
            String className = metadataReaderFactory.getMetadataReader(resource).getClassMetadata().getClassName();
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

    /**
     * Lombok {@code @Builder}가 만드는 중첩 빌더는 직렬화 대상이 아니다.
     * 빌더는 대상 DTO의 필드를 복제해 갖고 있어 걸러내지 않으면 같은 필드가 두 번 걸린다.
     */
    private static boolean isGeneratedBuilder(Class<?> candidate) {
        Class<?> enclosing = candidate.getEnclosingClass();
        return enclosing != null && candidate.getSimpleName().equals(enclosing.getSimpleName() + "Builder");
    }
}
