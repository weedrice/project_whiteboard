package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDraftCleanupBatchServiceTest {
    @Mock DraftPostRepository draftPostRepository;
    @Mock FileService fileService;

    @Test
    void cleanupExpiredBatch_marksFilesBeforeDeletingDrafts() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 4, 14, 0, 0);
        DraftPost expired = DraftPost.builder().build();
        ReflectionTestUtils.setField(expired, "draftId", 3L);
        when(draftPostRepository.findExpiredBefore(eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(expired));
        PostDraftCleanupBatchService service = new PostDraftCleanupBatchService(draftPostRepository, fileService);

        assertThat(service.cleanupExpiredBatch(cutoff, 100)).isEqualTo(1);
        var ordered = inOrder(fileService, draftPostRepository);
        ordered.verify(fileService).markDraftFilesDeletionPending(3L);
        ordered.verify(draftPostRepository).delete(expired);
        ordered.verify(draftPostRepository).flush();
    }

    @Test
    void cleanupExpiredBatch_requiresNewTransaction() throws Exception {
        Method method = PostDraftCleanupBatchService.class.getMethod(
                "cleanupExpiredBatch", LocalDateTime.class, int.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
