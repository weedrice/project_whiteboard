package com.weedrice.whiteboard.domain.tag.repository;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.springframework.context.annotation.Import;

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
}
