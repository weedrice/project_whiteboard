package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagCleanupServiceTest {

    @Mock
    private TagRepository tagRepository;

    private TagCleanupService tagCleanupService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-11T18:30:00Z"), DateTimeUtils.KST_ZONE_ID);
        tagCleanupService = new TagCleanupService(tagRepository, clock);
    }

    @Test
    void cleanupOrphanTags_deletesTagsOlderThanTwentyFourHours() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 11, 3, 30);
        when(tagRepository.deleteOrphanTagsCreatedBefore(cutoff)).thenReturn(3);

        int deletedCount = tagCleanupService.cleanupOrphanTags();

        assertThat(deletedCount).isEqualTo(3);
        verify(tagRepository).deleteOrphanTagsCreatedBefore(cutoff);
    }
}
