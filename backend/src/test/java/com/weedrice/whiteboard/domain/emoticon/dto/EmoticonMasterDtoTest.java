package com.weedrice.whiteboard.domain.emoticon.dto;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonMasterDto tests")
class EmoticonMasterDtoTest {

    private EmoticonMaster createMaster(Long emoticonId, User creator, String isActive, List<EmoticonImage> images) {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("test")
                .thumbnailUrl("thumb")
                .tags(List.of("a"))
                .creator(creator)
                .build();
        ReflectionTestUtils.setField(master, "emoticonId", emoticonId);
        ReflectionTestUtils.setField(master, "isActive", isActive);
        ReflectionTestUtils.setField(master, "purchaseCount", 5);
        ReflectionTestUtils.setField(master, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(master, "modifiedAt", LocalDateTime.now());
        if (images != null) {
            ReflectionTestUtils.setField(master, "images", images);
        }
        return master;
    }

    @Nested
    @DisplayName("from()")
    class From {

        @Test
        @DisplayName("creator가 없으면 creatorId와 creatorName은 null")
        void from_creatorNull() {
            EmoticonMaster master = createMaster(1L, null, "Y", Collections.emptyList());

            EmoticonMasterDto dto = EmoticonMasterDto.from(master);

            assertThat(dto.getEmoticonId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("test");
            assertThat(dto.getCreatorId()).isNull();
            assertThat(dto.getCreatorName()).isNull();
            assertThat(dto.getIsActive()).isTrue();
            assertThat(dto.getPurchaseCount()).isEqualTo(5);
            assertThat(dto.getImages()).isEmpty();
        }

        @Test
        @DisplayName("creator가 있으면 creator 정보를 포함한다")
        void from_creatorPresent() {
            User user = User.builder().loginId("u").displayName("writer").email("e@e.com").password("p").build();
            ReflectionTestUtils.setField(user, "userId", 10L);
            EmoticonMaster master = createMaster(1L, user, "Y", Collections.emptyList());

            EmoticonMasterDto dto = EmoticonMasterDto.from(master);

            assertThat(dto.getCreatorId()).isEqualTo(10L);
            assertThat(dto.getCreatorName()).isEqualTo("writer");
        }

        @Test
        @DisplayName("isActive가 N이면 false")
        void from_isActiveN() {
            EmoticonMaster master = createMaster(1L, null, "N", Collections.emptyList());

            EmoticonMasterDto dto = EmoticonMasterDto.from(master);

            assertThat(dto.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("images를 포함한다")
        void from_withImages() {
            EmoticonMaster master = createMaster(1L, null, "Y", Collections.emptyList());
            EmoticonImage img = EmoticonImage.builder()
                    .emoticonMaster(master)
                    .imageUrl("url")
                    .sortOrder(0)
                    .build();
            ReflectionTestUtils.setField(img, "imageId", 100L);
            ReflectionTestUtils.setField(master, "images", List.of(img));

            EmoticonMasterDto dto = EmoticonMasterDto.from(master);

            assertThat(dto.getImages()).hasSize(1);
            assertThat(dto.getImages().get(0).getImageId()).isEqualTo(100L);
            assertThat(dto.getImages().get(0).getImageUrl()).isEqualTo("url");
        }
    }

    @Nested
    @DisplayName("fromWithoutImages()")
    class FromWithoutImages {

        @Test
        @DisplayName("creator가 없으면 creatorId와 creatorName은 null")
        void fromWithoutImages_creatorNull() {
            EmoticonMaster master = createMaster(2L, null, "Y", null);

            EmoticonMasterDto dto = EmoticonMasterDto.fromWithoutImages(master);

            assertThat(dto.getEmoticonId()).isEqualTo(2L);
            assertThat(dto.getCreatorId()).isNull();
            assertThat(dto.getCreatorName()).isNull();
            assertThat(dto.getImages()).isNull();
        }

        @Test
        @DisplayName("기본 목록 변환은 creator 이름을 지연 로딩하지 않는다")
        void fromWithoutImages_creatorPresentIsActiveN() {
            User user = User.builder().loginId("u").displayName("name").email("e@e.com").password("p").build();
            ReflectionTestUtils.setField(user, "userId", 7L);
            EmoticonMaster master = createMaster(3L, user, "N", null);

            EmoticonMasterDto dto = EmoticonMasterDto.fromWithoutImages(master);

            assertThat(dto.getCreatorId()).isEqualTo(7L);
            assertThat(dto.getCreatorName()).isNull();
            assertThat(dto.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("creator 요약 정보를 명시적으로 주입할 수 있다")
        void fromWithoutImages_withExplicitCreatorSummary() {
            EmoticonMaster master = createMaster(4L, null, "Y", null);

            EmoticonMasterDto dto = EmoticonMasterDto.fromWithoutImages(master, 9L, "creator");

            assertThat(dto.getCreatorId()).isEqualTo(9L);
            assertThat(dto.getCreatorName()).isEqualTo("creator");
        }
    }

    @Test
    @DisplayName("builder getter smoke test")
    void builderAndGetters() {
        EmoticonMasterDto dto = EmoticonMasterDto.builder()
                .emoticonId(1L)
                .name("n")
                .thumbnailUrl("u")
                .tags(List.of("t"))
                .isActive(true)
                .creatorId(2L)
                .creatorName("c")
                .purchaseCount(10)
                .images(Collections.emptyList())
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();

        assertThat(dto.getEmoticonId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("n");
        assertThat(dto.getThumbnailUrl()).isEqualTo("u");
        assertThat(dto.getTags()).containsExactly("t");
        assertThat(dto.getIsActive()).isTrue();
        assertThat(dto.getCreatorId()).isEqualTo(2L);
        assertThat(dto.getCreatorName()).isEqualTo("c");
        assertThat(dto.getPurchaseCount()).isEqualTo(10);
        assertThat(dto.getImages()).isEmpty();
    }
}
