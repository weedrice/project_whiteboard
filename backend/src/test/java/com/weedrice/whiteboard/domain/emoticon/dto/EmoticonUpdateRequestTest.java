package com.weedrice.whiteboard.domain.emoticon.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonUpdateRequest 테스트")
class EmoticonUpdateRequestTest {

    @Test
    @DisplayName("builder / getter - 모든 필드")
    void builderAndGetters() {
        EmoticonUpdateRequest req = EmoticonUpdateRequest.builder()
                .name("수정이름")
                .thumbnailUrl("https://new-thumb.png")
                .tags(List.of("x", "y"))
                .build();

        assertThat(req.getName()).isEqualTo("수정이름");
        assertThat(req.getThumbnailUrl()).isEqualTo("https://new-thumb.png");
        assertThat(req.getTags()).containsExactly("x", "y");
    }

    @Test
    @DisplayName("NoArgsConstructor / AllArgsConstructor")
    void noArgsAndAllArgs() {
        EmoticonUpdateRequest empty = new EmoticonUpdateRequest();
        assertThat(empty.getName()).isNull();
        assertThat(empty.getThumbnailUrl()).isNull();
        assertThat(empty.getTags()).isNull();

        EmoticonUpdateRequest full = new EmoticonUpdateRequest("n", "u", List.of("t"));
        assertThat(full.getName()).isEqualTo("n");
        assertThat(full.getThumbnailUrl()).isEqualTo("u");
        assertThat(full.getTags()).containsExactly("t");
    }
}
