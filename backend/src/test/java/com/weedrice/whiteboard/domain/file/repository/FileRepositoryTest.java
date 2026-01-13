package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class FileRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FileRepository fileRepository;

    private User uploader;
    private File file;

    @BeforeEach
    void setUp() {
        uploader = User.builder()
                .loginId("uploader")
                .email("uploader@test.com")
                .password("password")
                .displayName("Uploader")
                .build();
        entityManager.persist(uploader);

        file = File.builder()
                .originalName("test.jpg")
                .filePath("path/to/test.jpg")
                .fileSize(1024L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(1L)
                .relatedType("POST_CONTENT")
                .build();
        entityManager.persist(file);
        entityManager.flush();
    }

    @Test
    @DisplayName("관련 ID와 타입으로 파일 조회")
    void findByRelatedIdAndRelatedType_success() {
        // when
        List<File> files = fileRepository.findByRelatedIdAndRelatedType(1L, "POST_CONTENT");

        // then
        assertThat(files).isNotEmpty();
        assertThat(files.get(0).getRelatedId()).isEqualTo(1L);
        assertThat(files.get(0).getRelatedType()).isEqualTo("POST_CONTENT");
    }

    @Test
    @DisplayName("관련 ID 목록과 타입으로 파일 조회")
    void findByRelatedIdInAndRelatedType_success() {
        // given
        List<Long> relatedIds = List.of(1L, 2L);

        // when
        List<File> files = fileRepository.findByRelatedIdInAndRelatedType(relatedIds, "POST_CONTENT");

        // then
        assertThat(files).isNotEmpty();
    }

    @Test
    @DisplayName("이미지 파일이 있는 관련 ID 조회")
    void findRelatedIdsWithImages_success() {
        // given
        List<Long> relatedIds = List.of(1L);

        // when
        List<Long> idsWithImages = fileRepository.findRelatedIdsWithImages(relatedIds, "POST_CONTENT");

        // then
        assertThat(idsWithImages).isNotEmpty();
    }

    @Test
    @DisplayName("관련 ID가 null이고 생성일이 이전인 파일 조회")
    void findByRelatedIdIsNullAndCreatedAtBefore_success() {
        // given
        File orphanFile = File.builder()
                .originalName("orphan.jpg")
                .filePath("path/to/orphan.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(null)
                .relatedType(null)
                .build();
        entityManager.persist(orphanFile);
        entityManager.flush();

        // when
        List<File> files = fileRepository.findByRelatedIdIsNullAndCreatedAtBefore(LocalDateTime.now().plusDays(1));

        // then
        assertThat(files).isNotEmpty();
    }

    @Test
    @DisplayName("특정 관련 ID, 타입, MIME 타입으로 첫 번째 파일 조회")
    void findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWith_success() {
        // when
        Optional<File> found = fileRepository.findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWith(
                1L, "POST_CONTENT", "image/");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getMimeType()).startsWith("image/");
    }
}
