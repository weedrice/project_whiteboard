package com.weedrice.whiteboard.domain.tag.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
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
    @DisplayName("없는 태그를 insert-ignore로 생성한다")
    void insertIgnore_createsMissingTag() {
        int inserted = tagRepository.insertIgnore("inserted-tag");
        entityManager.flush();
        entityManager.clear();

        Optional<Tag> insertedTag = tagRepository.findByTagName("inserted-tag");
        assertThat(inserted).isEqualTo(1);
        assertThat(insertedTag).isPresent();
        assertThat(insertedTag.get().getPostCount()).isZero();
        assertThat(insertedTag.get().getCreatedAt()).isNotNull();
        assertThat(insertedTag.get().getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("중복 태그 이름은 insert-ignore에서 건너뛴다")
    void insertIgnore_skipsDuplicateTagName() {
        int inserted = tagRepository.insertIgnore("test-tag");
        entityManager.flush();
        entityManager.clear();

        assertThat(inserted).isZero();
        assertThat(tagRepository.findByTagName("test-tag")).isPresent();
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

    @Test
    @DisplayName("24시간 초과 고아 태그를 삭제한다")
    void deleteOrphanTagsCreatedBefore_deletesOldZeroCountOrphans() {
        Tag orphanTag = new Tag("old-orphan-tag");
        entityManager.persist(orphanTag);
        entityManager.flush();
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 11, 3, 0);
        setTagCreatedAt(orphanTag, cutoff.minusHours(1));

        int deleted = tagRepository.deleteOrphanTagsCreatedBefore(cutoff);
        entityManager.flush();

        assertThat(deleted).isEqualTo(1);
        assertThat(tagRepository.findById(orphanTag.getTagId())).isEmpty();
    }

    @Test
    @DisplayName("연결된 태그와 사용 중인 태그와 최신 태그는 삭제하지 않는다")
    void deleteOrphanTagsCreatedBefore_keepsNonOrphans() {
        User user = User.builder()
                .loginId("tag-clean-user")
                .email("tag-clean@test.com")
                .password("password")
                .displayName("Tag Clean User")
                .build();
        entityManager.persist(user);
        Board board = Board.builder()
                .boardName("Tag Clean Board")
                .boardUrl("tag-clean-board")
                .creator(user)
                .build();
        entityManager.persist(board);
        Post post = Post.builder()
                .title("Tag clean post")
                .contents("contents")
                .user(user)
                .board(board)
                .build();
        entityManager.persist(post);

        Tag linkedTag = new Tag("linked-tag");
        Tag countedTag = new Tag("counted-tag");
        countedTag.incrementPostCount();
        Tag recentTag = new Tag("recent-tag");
        Tag boundaryTag = new Tag("boundary-tag");
        entityManager.persist(linkedTag);
        entityManager.persist(countedTag);
        entityManager.persist(recentTag);
        entityManager.persist(boundaryTag);
        entityManager.persist(PostTag.builder().post(post).tag(linkedTag).build());
        entityManager.flush();

        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 11, 3, 0);
        setTagCreatedAt(linkedTag, cutoff.minusHours(1));
        setTagCreatedAt(countedTag, cutoff.minusHours(1));
        setTagCreatedAt(recentTag, cutoff.plusMinutes(1));
        setTagCreatedAt(boundaryTag, cutoff);

        int deleted = tagRepository.deleteOrphanTagsCreatedBefore(cutoff);
        entityManager.flush();

        assertThat(deleted).isZero();
        assertThat(tagRepository.findById(linkedTag.getTagId())).isPresent();
        assertThat(tagRepository.findById(countedTag.getTagId())).isPresent();
        assertThat(tagRepository.findById(recentTag.getTagId())).isPresent();
        assertThat(tagRepository.findById(boundaryTag.getTagId())).isPresent();
    }

    private void setTagCreatedAt(Tag tag, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE tags SET created_at = ?, modified_at = ? WHERE tag_id = ?")
                .setParameter(1, createdAt)
                .setParameter(2, createdAt)
                .setParameter(3, tag.getTagId())
                .executeUpdate();
        entityManager.clear();
    }
}
