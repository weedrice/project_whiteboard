package com.weedrice.whiteboard.domain.emoticon.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonUpdateRequest 테스트")
class EmoticonUpdateRequestTest {

    @Test
    @DisplayName("builder와 getter가 fileId 기반 필드를 노출한다")
    void builderAndGetters() {
        EmoticonUpdateRequest req = EmoticonUpdateRequest.builder()
                .name("수정이름")
                .thumbnailFileId(10L)
                .tags(List.of("x", "y"))
                .build();

        assertThat(req.getName()).isEqualTo("수정이름");
        assertThat(req.getThumbnailFileId()).isEqualTo(10L);
        assertThat(req.getTags()).containsExactly("x", "y");
    }

    @Test
    @DisplayName("기본 생성자와 전체 생성자가 동작한다")
    void constructors() {
        EmoticonUpdateRequest empty = new EmoticonUpdateRequest();
        assertThat(empty.getName()).isNull();
        assertThat(empty.getThumbnailFileId()).isNull();
        assertThat(empty.getTags()).isNull();

        EmoticonUpdateRequest full = new EmoticonUpdateRequest("n", 1L, List.of("t"));
        assertThat(full.getName()).isEqualTo("n");
        assertThat(full.getThumbnailFileId()).isEqualTo(1L);
        assertThat(full.getTags()).containsExactly("t");
    }
}
