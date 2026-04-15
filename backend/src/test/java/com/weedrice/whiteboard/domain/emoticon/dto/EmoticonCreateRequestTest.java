package com.weedrice.whiteboard.domain.emoticon.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonCreateRequest 테스트")
class EmoticonCreateRequestTest {

    @Test
    @DisplayName("builder와 getter가 fileId 기반 필드를 노출한다")
    void builderAndGetters() {
        EmoticonCreateRequest req = EmoticonCreateRequest.builder()
                .name("이모티콘")
                .thumbnailFileId(10L)
                .tags(List.of("a", "b"))
                .imageFileIds(List.of(11L, 12L))
                .build();

        assertThat(req.getName()).isEqualTo("이모티콘");
        assertThat(req.getThumbnailFileId()).isEqualTo(10L);
        assertThat(req.getTags()).containsExactly("a", "b");
        assertThat(req.getImageFileIds()).containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("기본 생성자와 전체 생성자가 동작한다")
    void constructors() {
        EmoticonCreateRequest empty = new EmoticonCreateRequest();
        assertThat(empty.getName()).isNull();
        assertThat(empty.getThumbnailFileId()).isNull();
        assertThat(empty.getTags()).isNull();
        assertThat(empty.getImageFileIds()).isNull();

        EmoticonCreateRequest full = new EmoticonCreateRequest("n", 1L, List.of("t"), List.of(2L));
        assertThat(full.getName()).isEqualTo("n");
        assertThat(full.getThumbnailFileId()).isEqualTo(1L);
        assertThat(full.getTags()).containsExactly("t");
        assertThat(full.getImageFileIds()).containsExactly(2L);
    }
}
