package com.weedrice.whiteboard.global.common.dto;

import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `boolean isXxx` 필드가 wire에서 **읽히는지**를 고정한다.
 *
 * <p>{@link BooleanWireNameContractTest}는 직렬화만 본다. 그것만으로는 부족하다는 것이
 * 실제 사고로 드러났다. A8 1차 작업에서 `@JsonProperty`를 필드에서 getter로 옮겼더니
 * 키는 하나가 되었지만 <b>속성이 읽기 전용이 되어</b> 요청 본문의 `isSecret`이 무시됐다.
 *
 * <p>원인: Lombok이 primitive `boolean isXxx`에 만드는 getter는 `isXxx()`이고, Jackson이
 * 유추하는 이름은 `is`가 떨어진 `xxx`다. 필드의 유추 이름은 `isXxx`라 서로 다른 속성으로
 * 잡힌다. getter에만 명시 이름을 주면 그 속성에는 mutator가 없다. 필드와 getter <b>양쪽</b>에
 * 같은 이름을 줘야 하나의 속성으로 합쳐져 읽기·쓰기가 모두 된다.
 *
 * <p>`Boolean` 래퍼는 getter가 `getIsXxx()`라 유추 이름이 `isXxx`로 같아 우연히 동작했다.
 * 그 우연에 기대지 않도록 여기서 primitive와 래퍼를 함께 고정한다.
 *
 * <p>직렬화 테스트만으로는 이 회귀가 잡히지 않는다. 요청 DTO를 다루는 컨트롤러 테스트가
 * 전부 서비스를 목으로 대체해 역직렬화 결과를 검증하지 않기 때문이다.
 */
class BooleanWireRoundTripContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String POST_FLAGS = """
            {"title":"t","contents":"c",
             "isNotice":true,"isNsfw":true,"isSpoiler":true,"isSecret":true}
            """;

    @Test
    @DisplayName("PostCreateRequest는 isXxx 플래그를 읽는다")
    void postCreateRequestReadsFlags() {
        PostCreateRequest request = MAPPER.readValue(POST_FLAGS, PostCreateRequest.class);

        assertThat(request.isNotice()).as("isNotice").isTrue();
        assertThat(request.isNsfw()).as("isNsfw").isTrue();
        assertThat(request.isSpoiler()).as("isSpoiler").isTrue();
        // isSecret이 무시되면 비밀글이 공개로 생성된다. 이 검사가 그 회귀를 막는다.
        assertThat(request.isSecret()).as("isSecret").isTrue();
    }

    @Test
    @DisplayName("PostUpdateRequest는 isXxx 플래그를 읽는다")
    void postUpdateRequestReadsFlags() {
        PostUpdateRequest request = MAPPER.readValue(POST_FLAGS, PostUpdateRequest.class);

        assertThat(request.getIsNotice()).as("isNotice").isTrue();
        assertThat(request.isNsfw()).as("isNsfw").isTrue();
        assertThat(request.isSpoiler()).as("isSpoiler").isTrue();
        // 수정 경로에서 무시되면 기존 비밀글이 수정 한 번에 공개로 바뀐다.
        assertThat(request.isSecret()).as("isSecret").isTrue();
    }

    @Test
    @DisplayName("PostDraftRequest는 isXxx 플래그를 읽는다")
    void postDraftRequestReadsFlags() {
        PostDraftRequest request = MAPPER.readValue(POST_FLAGS, PostDraftRequest.class);

        assertThat(request.isNotice()).as("isNotice").isTrue();
        assertThat(request.isNsfw()).as("isNsfw").isTrue();
        assertThat(request.isSpoiler()).as("isSpoiler").isTrue();
        assertThat(request.isSecret()).as("isSecret").isTrue();
    }

    @Test
    @DisplayName("ScheduledPostRequest는 isXxx 플래그를 읽는다")
    void scheduledPostRequestReadsFlags() {
        ScheduledPostRequest request = MAPPER.readValue("""
                {"boardId":1,"title":"t","contents":"c","scheduledAt":"2026-08-01T10:00:00",
                 "isNotice":true,"isNsfw":true,"isSpoiler":true,"isSecret":true}
                """, ScheduledPostRequest.class);

        assertThat(request.isNotice()).as("isNotice").isTrue();
        assertThat(request.isNsfw()).as("isNsfw").isTrue();
        assertThat(request.isSpoiler()).as("isSpoiler").isTrue();
        assertThat(request.isSecret()).as("isSecret").isTrue();
    }

    @Test
    @DisplayName("CategoryRequest는 Boolean 래퍼 플래그를 읽는다")
    void categoryRequestReadsWrapperFlag() {
        CategoryRequest request = MAPPER.readValue(
                "{\"categoryName\":\"c\",\"isDefault\":true}", CategoryRequest.class);

        assertThat(request.getIsDefault()).as("isDefault").isTrue();
    }

    @Test
    @DisplayName("응답으로 나간 값을 그대로 되돌려 보내면 같은 값으로 읽힌다")
    void roundTripsThroughTheWire() {
        // 클라이언트가 응답을 그대로 되돌려 보내는 경로(임시저장 등)가 실제로 있다.
        // 직렬화 이름과 역직렬화 이름이 어긋나면 여기서 걸린다.
        PostCreateRequest original = MAPPER.readValue(POST_FLAGS, PostCreateRequest.class);
        PostCreateRequest reread = MAPPER.readValue(
                MAPPER.writeValueAsString(original), PostCreateRequest.class);

        assertThat(reread.isNotice()).isTrue();
        assertThat(reread.isNsfw()).isTrue();
        assertThat(reread.isSpoiler()).isTrue();
        assertThat(reread.isSecret()).isTrue();
    }
}
