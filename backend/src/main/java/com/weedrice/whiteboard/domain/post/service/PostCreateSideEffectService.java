package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.port.PostMentionPort;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostCreateSideEffectService {

    private final TagAssignmentService tagAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final ContentRewardService contentRewardService;
    private final FileService fileService;
    private final SemanticSearchEventPublisher semanticSearchEventPublisher;
    private final PostVersionRecorder postVersionRecorder;
    private final PostDraftPublicationService postDraftPublicationService;
    private final PostMentionPort postMentionPort;
    private final PollService pollService;
    private final BadgeEvaluationService badgeEvaluationService;

    public int applyAfterCreate(
            Long userId,
            ActorUserPrincipal user,
            Long boardId,
            Post savedPost,
            PostCreateRequest request,
            Long publishingScheduledPostId) {
        tagAssignmentService.assignTags(savedPost, request.getTags());
        pollService.createPoll(savedPost, request.getPoll());
        postVersionRecorder.record(savedPost, user, "CREATE", null, null);

        DraftPost publishedDraft = postDraftPublicationService.lockAndValidateForPublication(
                request.getDraftId(), user, savedPost.getBoard(), null, publishingScheduledPostId);
        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            fileService.attachFilesToPost(request.getFileIds(), userId, savedPost.getPostId(), request.getDraftId());
        }
        postDraftPublicationService.deletePublishedDraft(publishedDraft);

        int earnedPoints = contentRewardService.rewardCreate(userId, savedPost.getPostId(), ContentRewardPolicy.POST);
        postMentionPort.publishMentions(userId, savedPost.getAgentId(), NotificationSourceType.POST, savedPost.getPostId(),
                savedPost.getContents());
        eventPublisher.publishEvent(new PostPublishedEvent(savedPost.getPostId(), boardId));
        semanticSearchEventPublisher.publish("POST", savedPost.getPostId(), SemanticSearchIndexAction.UPSERT);
        badgeEvaluationService.evaluatePostCountBadges(userId);
        return earnedPoints;
    }
}
