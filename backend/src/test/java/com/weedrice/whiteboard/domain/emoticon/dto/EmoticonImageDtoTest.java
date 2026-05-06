package com.weedrice.whiteboard.domain.emoticon.dto;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonImageDto 테스트")
class EmoticonImageDtoTest {

    @Test
    @DisplayName("from() - 모든 필드 매핑")
    void from_allFields() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(Collections.emptyList())
                .creator(null)
                .build();
        ReflectionTestUtils.setField(master, "emoticonId", 5L);

        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(master)
                .imageUrl("https://example.com/img.png")
                .sortOrder(2)
                .build();
        ReflectionTestUtils.setField(image, "imageId", 20L);

        EmoticonImageDto dto = EmoticonImageDto.from(image);

        assertThat(dto.getImageId()).isEqualTo(20L);
        assertThat(dto.getEmoticonId()).isEqualTo(5L);
        assertThat(dto.getImageUrl()).isEqualTo("https://example.com/img.png");
        assertThat(dto.getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("builder / AllArgsConstructor")
    void builderAndGetters() {
        EmoticonImageDto dto = EmoticonImageDto.builder()
                .imageId(1L)
                .emoticonId(2L)
                .imageUrl("url")
                .sortOrder(0)
                .build();

        assertThat(dto.getImageId()).isEqualTo(1L);
        assertThat(dto.getEmoticonId()).isEqualTo(2L);
        assertThat(dto.getImageUrl()).isEqualTo("url");
        assertThat(dto.getSortOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("from() - legacy /files URL을 canonical API URL로 변환")
    void from_normalizesLegacyFileUrl() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(Collections.emptyList())
                .creator(null)
                .build();
        ReflectionTestUtils.setField(master, "emoticonId", 5L);

        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(master)
                .imageUrl("/files/20")
                .sortOrder(2)
                .build();
        ReflectionTestUtils.setField(image, "imageId", 20L);

        EmoticonImageDto dto = EmoticonImageDto.from(image);

        assertThat(dto.getImageUrl()).isEqualTo("/api/v1/files/20");
    }
}
