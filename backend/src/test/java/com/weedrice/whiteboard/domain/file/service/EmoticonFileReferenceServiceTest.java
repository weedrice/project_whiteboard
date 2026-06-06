package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmoticonFileReferenceServiceTest {

    @Mock
    private EmoticonImageRepository emoticonImageRepository;
    @Mock
    private EmoticonMasterRepository emoticonMasterRepository;

    @InjectMocks
    private EmoticonFileReferenceService service;

    @Test
    @DisplayName("null file id is not referenced")
    void isReferenced_nullFileId_returnsFalse() {
        assertThat(service.isReferenced(null)).isFalse();

        verifyNoInteractions(emoticonImageRepository, emoticonMasterRepository);
    }

    @Test
    @DisplayName("image or thumbnail URL candidate marks file as referenced")
    void isReferenced_checksImageAndThumbnailReferenceCandidates() {
        List<String> candidateUrls = List.of("/api/v1/files/10", "/files/10");
        when(emoticonImageRepository.existsByImageUrlIn(candidateUrls)).thenReturn(false);
        when(emoticonMasterRepository.existsByThumbnailUrlIn(candidateUrls)).thenReturn(true);

        assertThat(service.isReferenced(10L)).isTrue();
    }

    @Test
    @DisplayName("referenced image and thumbnail file ids are excluded from cleanup")
    void excludeReferencedFileIds_removesImageAndThumbnailReferences() {
        List<String> candidateUrls = List.of(
                "/api/v1/files/10",
                "/files/10",
                "/api/v1/files/11",
                "/files/11",
                "/api/v1/files/12",
                "/files/12");
        when(emoticonImageRepository.findReferencedImageUrls(candidateUrls))
                .thenReturn(List.of("/api/v1/files/10"));
        when(emoticonMasterRepository.findReferencedThumbnailUrls(candidateUrls))
                .thenReturn(List.of("/files/11"));

        List<Long> cleanupFileIds = service.excludeReferencedFileIds(List.of(10L, 11L, 12L));

        assertThat(cleanupFileIds).containsExactly(12L);
    }
}
