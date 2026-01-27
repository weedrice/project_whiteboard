package com.weedrice.whiteboard.domain.emoticon.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonImage 엔티티 테스트")
class EmoticonImageTest {

    @Test
    @DisplayName("builder - sortOrder null이면 0")
    void builder_sortOrderNull() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(java.util.Collections.emptyList())
                .creator(null)
                .build();

        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(master)
                .imageUrl("https://example.com/img.png")
                .sortOrder(null)
                .build();

        assertThat(image.getSortOrder()).isEqualTo(0);
        assertThat(image.getImageUrl()).isEqualTo("https://example.com/img.png");
        assertThat(image.getEmoticonMaster()).isSameAs(master);
    }

    @Test
    @DisplayName("builder - sortOrder 존재")
    void builder_sortOrderPresent() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(java.util.Collections.emptyList())
                .creator(null)
                .build();

        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(master)
                .imageUrl("url")
                .sortOrder(5)
                .build();

        assertThat(image.getSortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateSortOrder")
    void updateSortOrder() {
        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(null)
                .imageUrl("url")
                .sortOrder(0)
                .build();

        image.updateSortOrder(3);
        assertThat(image.getSortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("updateImageUrl")
    void updateImageUrl() {
        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(null)
                .imageUrl("old")
                .sortOrder(0)
                .build();

        image.updateImageUrl("new");
        assertThat(image.getImageUrl()).isEqualTo("new");
    }

    @Test
    @DisplayName("setEmoticonMaster")
    void setEmoticonMaster() {
        EmoticonImage image = EmoticonImage.builder()
                .emoticonMaster(null)
                .imageUrl("url")
                .sortOrder(0)
                .build();
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(java.util.Collections.emptyList())
                .creator(null)
                .build();

        image.setEmoticonMaster(master);

        assertThat(image.getEmoticonMaster()).isSameAs(master);
    }
}
