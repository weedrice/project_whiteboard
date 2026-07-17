package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class FileVariantReconciliationRepositoryTest {

    @Autowired FileRepository fileRepository;
    @Autowired TestEntityManager entityManager;

    @Test
    void reconciledSmallMediumAndLargeImagesAreNotSelectedAgain() {
        long existingMaxFileId = fileRepository.findAll().stream()
                .map(File::getFileId)
                .max(Long::compareTo)
                .orElse(0L);
        User user = User.builder()
                .loginId("variant-owner")
                .email("variant-owner@example.com")
                .password("password")
                .displayName("Variant Owner")
                .build();
        entityManager.persist(user);
        File small = persistFile(user, "small.jpg", 100, 80, 0);
        File medium = persistFile(user, "medium.jpg", 800, 600, 1);
        File large = persistFile(user, "large.jpg", 2000, 1500, 2);
        persistVariant(medium, FileVariantType.THUMBNAIL, 320, 240);
        persistVariant(large, FileVariantType.THUMBNAIL, 320, 240);
        persistVariant(large, FileVariantType.MEDIUM, 1280, 960);
        entityManager.flush();
        entityManager.clear();

        assertThat(fileRepository.findActiveImagesMissingVariantsAfter(
                existingMaxFileId, 1, PageRequest.of(0, 100)))
                .extracting(File::getFilePath)
                .isEmpty();
    }

    private File persistFile(User user, String path, int width, int height, int expected) {
        File file = File.builder()
                .filePath(path)
                .originalName(path)
                .fileSize(100L)
                .mimeType("image/jpeg")
                .uploader(user)
                .storageStatus(FileStorageStatus.ACTIVE)
                .imageWidth(width)
                .imageHeight(height)
                .expectedVariantCount(expected)
                .variantReconciliationVersion(1)
                .build();
        entityManager.persist(file);
        return file;
    }

    private void persistVariant(File file, FileVariantType type, int width, int height) {
        entityManager.persist(FileVariant.builder()
                .file(file)
                .variantType(type)
                .filePath("variants/" + file.getFilePath() + "/" + type.name() + ".webp")
                .fileSize(50L)
                .mimeType("image/webp")
                .width(width)
                .height(height)
                .storageStatus(FileStorageStatus.ACTIVE)
                .build());
    }
}
