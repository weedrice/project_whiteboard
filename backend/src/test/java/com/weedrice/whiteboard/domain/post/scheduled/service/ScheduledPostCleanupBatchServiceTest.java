package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledPostCleanupBatchServiceTest {

    @Mock ScheduledPostRepository scheduledPostRepository;
    @Mock ScheduledPostFileService scheduledPostFileService;
    @InjectMocks ScheduledPostCleanupBatchService service;

    @Test
    void releasesDraftAndFileReferencesForExpiredFailedPosts() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime canceledAt = LocalDateTime.of(2026, 7, 1, 0, 0);
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .title("failed")
                .contents("body")
                .draftId(77L)
                .scheduledAt(cutoff.minusDays(1))
                .build();
        ReflectionTestUtils.setField(scheduledPost, "scheduledPostId", 9L);
        ReflectionTestUtils.setField(scheduledPost, "status", ScheduledPost.STATUS_FAILED);
        when(scheduledPostRepository.findExpiredFailedBefore(any(), any(Pageable.class)))
                .thenReturn(List.of(scheduledPost));

        int cleaned = service.cleanupExpiredFailedBatch(cutoff, canceledAt, 100);

        assertThat(cleaned).isEqualTo(1);
        assertThat(scheduledPost.getStatus()).isEqualTo(ScheduledPost.STATUS_CANCELED);
        assertThat(scheduledPost.getDraftId()).isNull();
        assertThat(scheduledPost.getCanceledAt()).isEqualTo(canceledAt);
        verify(scheduledPostFileService).removeReferences(9L);
        verify(scheduledPostRepository).flush();
    }
}
