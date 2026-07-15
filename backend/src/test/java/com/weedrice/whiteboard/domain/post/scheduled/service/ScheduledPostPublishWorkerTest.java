package com.weedrice.whiteboard.domain.post.scheduled.service;

import tools.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledPostPublishWorkerTest {

    @Test
    void scheduledPayloadCarriesSourceDraftIdToPublication() {
        User user = User.builder().email("scheduled@example.com").displayName("scheduled-user").build();
        Board board = Board.builder().boardName("Scheduled Board").boardUrl("scheduled-board").build();
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(user)
                .board(board)
                .title("Scheduled title")
                .contents("Scheduled contents")
                .fileIdsJson("[10,11]")
                .draftId(77L)
                .scheduledAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .build();

        PostCreateRequest request = new ScheduledPostPayloadMapper(new ObjectMapper())
                .toPostCreateRequest(scheduledPost);

        assertThat(request.getDraftId()).isEqualTo(77L);
        assertThat(request.getFileIds()).containsExactly(10L, 11L);
    }

    @Test
    void publicationStepsUseIndependentTransactions() throws Exception {
        assertRequiresNew("claim", Long.class, LocalDateTime.class, LocalDateTime.class);
        assertRequiresNew("publishClaimed", Long.class, LocalDateTime.class);
        assertRequiresNew("markFailed", Long.class, LocalDateTime.class, RuntimeException.class);
    }

    private void assertRequiresNew(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ScheduledPostPublishWorker.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
