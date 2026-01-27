package com.weedrice.whiteboard.domain.emoticon.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmoticonMaster 엔티티 테스트")
class EmoticonMasterTest {

    @Test
    @DisplayName("builder - tags null이면 빈 리스트")
    void builder_tagsNull() {
        User creator = User.builder().loginId("u").displayName("U").email("e@e.com").password("p").build();
        ReflectionTestUtils.setField(creator, "userId", 1L);

        EmoticonMaster master = EmoticonMaster.builder()
                .name("이름")
                .thumbnailUrl("url")
                .tags(null)
                .creator(creator)
                .build();

        assertThat(master.getTags()).isEmpty();
        assertThat(master.getIsActive()).isEqualTo("Y");
        assertThat(master.getPurchaseCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("builder - tags 존재")
    void builder_tagsPresent() {
        User creator = User.builder().loginId("u").displayName("U").email("e@e.com").password("p").build();
        List<String> tags = Arrays.asList("a", "b");

        EmoticonMaster master = EmoticonMaster.builder()
                .name("이름")
                .thumbnailUrl("url")
                .tags(tags)
                .creator(creator)
                .build();

        assertThat(master.getTags()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("update - tags null이면 빈 리스트")
    void update_tagsNull() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("Old")
                .thumbnailUrl("thumb")
                .tags(List.of("x"))
                .creator(null)
                .build();

        master.update("New", "newThumb", null);

        assertThat(master.getName()).isEqualTo("New");
        assertThat(master.getThumbnailUrl()).isEqualTo("newThumb");
        assertThat(master.getTags()).isEmpty();
    }

    @Test
    @DisplayName("update - 모든 필드 변경")
    void update_allFields() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("Old")
                .thumbnailUrl("oldThumb")
                .tags(Collections.emptyList())
                .creator(null)
                .build();

        master.update("New", "newThumb", List.of("t1", "t2"));

        assertThat(master.getName()).isEqualTo("New");
        assertThat(master.getThumbnailUrl()).isEqualTo("newThumb");
        assertThat(master.getTags()).containsExactly("t1", "t2");
    }

    @Test
    @DisplayName("activate / deactivate")
    void activateDeactivate() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(Collections.emptyList())
                .creator(null)
                .build();

        assertThat(master.getIsActive()).isEqualTo("Y");
        master.deactivate();
        assertThat(master.getIsActive()).isEqualTo("N");
        master.activate();
        assertThat(master.getIsActive()).isEqualTo("Y");
    }

    @Test
    @DisplayName("addImage / removeImage")
    void addRemoveImage() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(Collections.emptyList())
                .creator(null)
                .build();
        EmoticonImage img = EmoticonImage.builder()
                .emoticonMaster(null)
                .imageUrl("url")
                .sortOrder(0)
                .build();

        master.addImage(img);

        assertThat(master.getImages()).containsExactly(img);
        assertThat(img.getEmoticonMaster()).isSameAs(master);

        master.removeImage(img);
        assertThat(master.getImages()).isEmpty();
        assertThat(img.getEmoticonMaster()).isNull();
    }

    @Test
    @DisplayName("isOwner - creator null이면 false")
    void isOwner_creatorNull() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(Collections.emptyList())
                .creator(null)
                .build();

        assertThat(master.isOwner(1L)).isFalse();
    }

    @Test
    @DisplayName("isOwner - userId 일치하면 true")
    void isOwner_match() {
        User creator = User.builder().loginId("u").displayName("U").email("e@e.com").password("p").build();
        ReflectionTestUtils.setField(creator, "userId", 10L);

        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(Collections.emptyList())
                .creator(creator)
                .build();

        assertThat(master.isOwner(10L)).isTrue();
        assertThat(master.isOwner(99L)).isFalse();
    }

    @Test
    @DisplayName("incrementPurchaseCount")
    void incrementPurchaseCount() {
        EmoticonMaster master = EmoticonMaster.builder()
                .name("n")
                .thumbnailUrl("u")
                .tags(Collections.emptyList())
                .creator(null)
                .build();

        assertThat(master.getPurchaseCount()).isEqualTo(0);
        master.incrementPurchaseCount();
        assertThat(master.getPurchaseCount()).isEqualTo(1);
        master.incrementPurchaseCount();
        assertThat(master.getPurchaseCount()).isEqualTo(2);
    }
}
