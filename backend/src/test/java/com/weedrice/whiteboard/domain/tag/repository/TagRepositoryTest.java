package com.weedrice.whiteboard.domain.tag.repository;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class TagRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TagRepository tagRepository;

    private Tag tag;

    @BeforeEach
    void setUp() {
        tag = new Tag("test-tag");
        tag.incrementPostCount();
        entityManager.persist(tag);
        entityManager.flush();
    }

    @Test
    @DisplayName("태그 이름으로 조회 성공")
    void findByTagName_success() {
        // when
        Optional<Tag> found = tagRepository.findByTagName("test-tag");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getTagName()).isEqualTo("test-tag");
    }

    @Test
    @DisplayName("태그 저장 및 조회 성공")
    void saveAndFind_success() {
        // given
        Tag newTag = new Tag("new-tag");

        // when
        Tag saved = tagRepository.save(newTag);
        Optional<Tag> found = tagRepository.findById(saved.getTagId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getTagName()).isEqualTo("new-tag");
    }

    @Test
    @DisplayName("tag names 컬렉션으로 태그를 조회한다")
    void findByTagNameIn_success() {
        Tag secondTag = new Tag("second-tag");
        entityManager.persist(secondTag);
        entityManager.flush();

        List<Tag> tags = tagRepository.findByTagNameIn(List.of("test-tag", "second-tag", "missing-tag"));

        assertThat(tags).extracting(Tag::getTagName)
                .containsExactlyInAnyOrder("test-tag", "second-tag");
    }

    @Test
    @DisplayName("tag id 컬렉션으로 게시글 수를 증가시킨다")
    void incrementPostCountIn_success() {
        int updated = tagRepository.incrementPostCountIn(List.of(tag.getTagId()));
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(tagRepository.findById(tag.getTagId()))
                .get()
                .extracting(Tag::getPostCount)
                .isEqualTo(2);
    }

    @Test
    @DisplayName("tag id 컬렉션으로 게시글 수를 감소시키되 음수로 만들지 않는다")
    void decrementPostCountIn_success() {
        Tag emptyTag = new Tag("empty-tag");
        entityManager.persist(emptyTag);
        entityManager.flush();

        int updated = tagRepository.decrementPostCountIn(List.of(tag.getTagId(), emptyTag.getTagId()));
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(2);
        assertThat(tagRepository.findById(tag.getTagId()))
                .get()
                .extracting(Tag::getPostCount)
                .isEqualTo(0);
        assertThat(tagRepository.findById(emptyTag.getTagId()))
                .get()
                .extracting(Tag::getPostCount)
                .isEqualTo(0);
    }
}
