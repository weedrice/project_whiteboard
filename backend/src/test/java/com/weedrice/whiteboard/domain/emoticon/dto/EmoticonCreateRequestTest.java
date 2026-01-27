package com.weedrice.whiteboard.domain.emoticon.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonCreateRequest 테스트")
class EmoticonCreateRequestTest {

    @Test
    @DisplayName("builder / getter - 모든 필드")
    void builderAndGetters() {
        EmoticonCreateRequest req = EmoticonCreateRequest.builder()
                .name("이모티콘")
                .thumbnailUrl("https://thumb.png")
                .tags(List.of("a", "b"))
                .imageUrls(List.of("https://1.png", "https://2.png"))
                .build();

        assertThat(req.getName()).isEqualTo("이모티콘");
        assertThat(req.getThumbnailUrl()).isEqualTo("https://thumb.png");
        assertThat(req.getTags()).containsExactly("a", "b");
        assertThat(req.getImageUrls()).containsExactly("https://1.png", "https://2.png");
    }

    @Test
    @DisplayName("NoArgsConstructor / AllArgsConstructor")
    void noArgsAndAllArgs() {
        EmoticonCreateRequest empty = new EmoticonCreateRequest();
        assertThat(empty.getName()).isNull();
        assertThat(empty.getThumbnailUrl()).isNull();
        assertThat(empty.getTags()).isNull();
        assertThat(empty.getImageUrls()).isNull();

        EmoticonCreateRequest full = new EmoticonCreateRequest("n", "u", List.of("t"), List.of("i"));
        assertThat(full.getName()).isEqualTo("n");
        assertThat(full.getThumbnailUrl()).isEqualTo("u");
        assertThat(full.getTags()).containsExactly("t");
        assertThat(full.getImageUrls()).containsExactly("i");
    }
}
