package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDraftCleanupServiceTest {

    @Mock
    private DraftPostRepository draftPostRepository;
    @Mock
    private FileService fileService;
    @Mock
    private PostDraftCleanupBatchService cleanupBatchService;

    private PostDraftCleanupService cleanupService;
    private User user;
    private Board board;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);
        cleanupService = new PostDraftCleanupService(draftPostRepository, fileService, clock, cleanupBatchService);
        user = User.builder().email("draft@example.com").displayName("draft-user").build();
        board = Board.builder().boardName("Draft Board").boardUrl("draft-board").build();
    }

    @Test
    @DisplayName("사용자 초안이 100개를 넘으면 가장 오래된 초안과 파일을 함께 정리한다")
    void enforceUserDraftLimit_deletesOldestDraftsWithFiles() {
        DraftPost first = draft(1L, "first");
        DraftPost second = draft(2L, "second");
        when(draftPostRepository.countByUser(user)).thenReturn(102L);
        when(draftPostRepository.findOldestByUser(eq(user), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        int deleted = cleanupService.enforceUserDraftLimit(user);

        assertThat(deleted).isEqualTo(2);
        var ordered = inOrder(fileService, draftPostRepository);
        ordered.verify(fileService).markDraftFilesDeletionPending(1L);
        ordered.verify(draftPostRepository).delete(first);
        ordered.verify(fileService).markDraftFilesDeletionPending(2L);
        ordered.verify(draftPostRepository).delete(second);
        ordered.verify(draftPostRepository).flush();
    }

    @Test
    @DisplayName("90일 초안 정리는 새 트랜잭션 배치를 완료될 때까지 반복한다")
    void cleanupExpiredDrafts_repeatsIndependentBatchesUntilPartialBatch() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 4, 14, 0, 0);
        when(cleanupBatchService.cleanupExpiredBatch(cutoff, 100))
                .thenReturn(100, 2);

        int deleted = cleanupService.cleanupExpiredDrafts();

        assertThat(deleted).isEqualTo(102);
        org.mockito.Mockito.verify(cleanupBatchService, org.mockito.Mockito.times(2))
                .cleanupExpiredBatch(cutoff, 100);
    }

    private DraftPost draft(Long draftId, String title) {
        DraftPost draft = DraftPost.builder()
                .user(user)
                .board(board)
                .title(title)
                .contents("contents")
                .build();
        ReflectionTestUtils.setField(draft, "draftId", draftId);
        return draft;
    }
}
