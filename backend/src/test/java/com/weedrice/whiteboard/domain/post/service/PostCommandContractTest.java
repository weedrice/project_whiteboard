package com.weedrice.whiteboard.domain.post.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostCommandContractTest {

    @Mock PostRepository postRepository;
    @Mock BoardRepository boardRepository;
    @Mock BoardCategoryRepository boardCategoryRepository;
    @Mock TagAssignmentService tagAssignmentService;
    @Mock ContentRewardService contentRewardService;
    @Mock FileService fileService;
    @Mock UserWritableResolver userWritableResolver;
    @Mock SanctionService sanctionService;
    @Mock PostCreateTargetResolver postCreateTargetResolver;
    @Mock PostCreatePolicyValidator postCreatePolicyValidator;
    @Mock PostVersionRecorder postVersionRecorder;
    @Mock PostDraftPublicationService postDraftPublicationService;
    @Mock PostCreateSideEffectService postCreateSideEffectService;
    @Mock BoardAccessPolicy boardAccessPolicy;
    @Mock PostAuthorCommandPolicy postAuthorCommandPolicy;
    @Mock SemanticSearchEventPublisher semanticSearchEventPublisher;
    @Mock PostSeriesService postSeriesService;
    @Mock NotificationAccessInvalidationService notificationAccessInvalidationService;
    @InjectMocks PostCommandService service;

    @Test
    void updatePost_rejectsImmutablePollPayloadBeforeLoadingPost() {
        PollRequest poll = new PollRequest();
        poll.setQuestion("Question");
        poll.setOptions(List.of("A", "B"));
        PostUpdateRequest request = new PostUpdateRequest(
                null, "Title", "Content", List.of(), false, false, false,
                null, null, poll, null);

        assertThatThrownBy(() -> service.updatePost(1L, 2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(postRepository);
    }
}
