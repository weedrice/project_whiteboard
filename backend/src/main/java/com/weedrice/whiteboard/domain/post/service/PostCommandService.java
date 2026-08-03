package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.service.MentionService;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateResponse;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommandService {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final TagAssignmentService tagAssignmentService;
    private final ContentRewardService contentRewardService;
    private final FileService fileService;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final PostCreateTargetResolver postCreateTargetResolver;
    private final PostCreatePolicyValidator postCreatePolicyValidator;
    private final PostVersionRecorder postVersionRecorder;
    private final PostDraftPublicationService postDraftPublicationService;
    private final PostCreateSideEffectService postCreateSideEffectService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final SemanticSearchEventPublisher semanticSearchEventPublisher;
    private final PostSeriesService postSeriesService;
    private final NotificationAccessInvalidationService notificationAccessInvalidationService;
    private final MentionService mentionService;
    private final AnonymousReadCacheInvalidator anonymousReadCacheInvalidator;

    @Transactional
    public Long createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        return createPostWithResponse(userId, boardUrl, request).getPostId();
    }

    @Transactional
    public PostCreateResponse createPostWithResponse(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        return createPostWithResponse(userId, boardUrl, request, null);
    }

    public PostCreateResponse createScheduledPostWithResponse(@NonNull Long userId, String boardUrl,
            PostCreateRequest request, @NonNull Long scheduledPostId) {
        return createPostWithResponse(userId, boardUrl, request, scheduledPostId);
    }

    private PostCreateResponse createPostWithResponse(@NonNull Long userId, String boardUrl, PostCreateRequest request,
            Long publishingScheduledPostId) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        PostCreateTarget target = postCreateTargetResolver.resolveTargetByBoardUrl(userId, null, normalizedBoardUrl);
        CreatedPost createdPost = createPost(target, request, null, publishingScheduledPostId);
        return PostCreateResponse.builder()
                .postId(createdPost.post().getPostId())
                .earnedPoints(createdPost.earnedPoints() > 0 ? createdPost.earnedPoints() : null)
                .build();
    }

    @Transactional
    public Long createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, String boardUrl,
            PostCreateRequest request) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        PostCreateTarget target = postCreateTargetResolver.resolveTargetByBoardUrl(userId, agentId, normalizedBoardUrl);
        return createPost(target, request, null).post().getPostId();
    }

    @Transactional
    public Long createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, PostCreateRequest request,
            PostCreateContext context) {
        return createPost(userId, agentId, null, request, context).post().getPostId();
    }

    @Transactional
    public Long createPost(@NonNull Long userId, @NonNull Long boardId, PostCreateRequest request) {
        return createPost(userId, null, boardId, request, null).post().getPostId();
    }

    @Transactional
    public Long createPost(@NonNull Long userId, Long agentId, @NonNull Long boardId, PostCreateRequest request) {
        return createPost(userId, agentId, boardId, request, null).post().getPostId();
    }

    private CreatedPost createPost(@NonNull Long userId, Long agentId, Long boardId, PostCreateRequest request,
            PostCreateContext context) {
        PostCreateTarget target = postCreateTargetResolver.resolveTarget(userId, agentId, boardId, context);
        return createPost(target, request, context);
    }

    private CreatedPost createPost(PostCreateTarget target, PostCreateRequest request, PostCreateContext context) {
        return createPost(target, request, context, null);
    }

    private CreatedPost createPost(PostCreateTarget target, PostCreateRequest request, PostCreateContext context,
            Long publishingScheduledPostId) {
        postCreatePolicyValidator.validateBoardAndNotice(target, request);
        PostCreateCategoryTarget categoryTarget =
                postCreateTargetResolver.resolveCategory(target.board(), request.getCategoryId(), context);
        postCreatePolicyValidator.validateCategory(target, categoryTarget);

        tagAssignmentService.validateTags(request.getTags());
        PostTitleValidator.validate(request.getTitle());
        String sanitizedContents = sanitizePostContents(request.getContents());
        boolean isSecret = !boardAccessPolicy.isInquiryBoard(target.board()) && request.isSecret();

        Post post = Post.builder()
                .board(target.board())
                .user(target.user())
                .agent(target.agent())
                .category(categoryTarget.category())
                .title(request.getTitle())
                .contents(sanitizedContents)
                .isNotice(request.isNotice())
                .isNsfw(request.isNsfw())
                .isSpoiler(request.isSpoiler())
                .isSecret(isSecret)
                .build();

        Post savedPost = postRepository.save(post);
        int earnedPoints = postCreateSideEffectService.applyAfterCreate(
                target.user().getUserId(),
                target.user(),
                target.board().getBoardId(),
                savedPost,
                request,
                publishingScheduledPostId);
        postSeriesService.attachPostToSeries(target.user().getUserId(), savedPost, request.getSeriesId());
        anonymousReadCacheInvalidator.evictPostRelatedCachesAfterCommit();
        return new CreatedPost(savedPost, earnedPoints);
    }

    private record CreatedPost(Post post, int earnedPoints) {
    }

    @Transactional
    public Long updatePost(@NonNull Long userId, @NonNull Long postId, PostUpdateRequest request) {
        if (request.getPoll() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        User modifier = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(modifier);
        Long boardId = postRepository.findBoardIdByPostId(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Board board = boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!Objects.equals(boardId, post.getBoard().getBoardId())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        postAuthorCommandPolicy.validateAuthorCommand(post, modifier);
        BoardCategory category = resolveUpdatedCategory(post, board, request.getCategoryId());
        postAuthorCommandPolicy.validateBoardWritable(board, modifier);
        postAuthorCommandPolicy.validateAppliedCategoryWriteRole(board, modifier, category);
        tagAssignmentService.validateTags(request.getTags());
        PostTitleValidator.validate(request.getTitle());

        String originalTitle = post.getTitle();
        String originalContents = post.getContents();
        boolean originalSecret = Boolean.TRUE.equals(post.getIsSecret());
        String sanitizedContents = sanitizePostContents(request.getContents());

        boolean isSecret = !boardAccessPolicy.isInquiryBoard(board) && request.isSecret();
        boolean isNotice = resolveUpdatedNotice(post, modifier, request.getIsNotice());
        post.updatePost(category, request.getTitle(), sanitizedContents, isNotice, request.isNsfw(),
                request.isSpoiler(), isSecret);
        tagAssignmentService.assignTags(post, request.getTags());
        if (request.isSeriesIdPresent()) {
            postSeriesService.updatePostSeries(post.getUser().getUserId(), post, request.getSeriesId());
        }

        DraftPost publishedDraft = postDraftPublicationService.lockAndValidateForPublication(
                request.getDraftId(), modifier, board, post.getPostId(), null);
        if (request.getFileIds() != null) {
            fileService.syncPostFiles(request.getFileIds(), userId, post.getPostId(), request.getDraftId());
        }
        postDraftPublicationService.deletePublishedDraft(publishedDraft);

        postVersionRecorder.record(post, modifier, "MODIFY", originalTitle, originalContents);
        semanticSearchEventPublisher.publish("POST", post.getPostId(), SemanticSearchIndexAction.UPSERT);
        if (!Objects.equals(originalTitle, post.getTitle())) {
            semanticSearchEventPublisher.publishPostCommentsReindex(post.getPostId());
        }
        if (!originalSecret && Boolean.TRUE.equals(post.getIsSecret())) {
            notificationAccessInvalidationService.invalidateCommentTopicAfterCommit(post.getPostId());
        }
        mentionService.publishNewMentions(
                modifier,
                post.getAgent(),
                NotificationSourceType.POST,
                post.getPostId(),
                originalContents,
                post.getContents());
        anonymousReadCacheInvalidator.evictPostRelatedCachesAfterCommit();

        return post.getPostId();
    }

    private boolean resolveUpdatedNotice(Post post, User modifier, Boolean requestedNotice) {
        boolean currentNotice = Boolean.TRUE.equals(post.getIsNotice());
        if (requestedNotice == null) {
            return currentNotice;
        }

        boolean nextNotice = Boolean.TRUE.equals(requestedNotice);
        if (currentNotice != nextNotice && !boardAccessPolicy.hasBoardAdminAccess(post.getBoard(), modifier)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return nextNotice;
    }

    @Transactional
    public void deletePost(@NonNull Long userId, @NonNull Long postId) {
        User modifier = userWritableResolver.resolveForUpdate(userId);
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        postAuthorCommandPolicy.validateDeletable(post, modifier);

        deletePostWithSideEffects(post, modifier);
        notificationAccessInvalidationService.invalidateCommentTopicAfterCommit(post.getPostId());
    }

    @Transactional
    public void deleteAgentOwnedPost(@NonNull Post post, @NonNull Long agentId, @NonNull User modifier) {
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.getAgent() == null || !Objects.equals(post.getAgent().getAgentId(), agentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        deletePostWithSideEffects(post, modifier);
    }

    private void deletePostWithSideEffects(Post post, User modifier) {
        post.deletePost();
        tagAssignmentService.clearTags(post);
        postVersionRecorder.record(post, modifier, "DELETE", post.getTitle(), post.getContents());
        fileService.markPostContentFilesDeletionPending(post.getPostId());

        contentRewardService.rollbackCreateReward(modifier, post.getPostId(), ContentRewardPolicy.POST);
        semanticSearchEventPublisher.publish("POST", post.getPostId(), SemanticSearchIndexAction.DELETE);
        anonymousReadCacheInvalidator.evictPostRelatedCachesAfterCommit();
    }

    private BoardCategory findActiveCategory(Board board, Long categoryId) {
        if (board == null || categoryId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(categoryId, board.getBoardId(), true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private BoardCategory resolveUpdatedCategory(Post post, Board board, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        BoardCategory currentCategory = post.getCategory();
        if (currentCategory != null && Objects.equals(currentCategory.getCategoryId(), categoryId)) {
            if (!Boolean.TRUE.equals(currentCategory.getIsActive())
                    || currentCategory.getBoard() == null
                    || !Objects.equals(currentCategory.getBoard().getBoardId(), board.getBoardId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return currentCategory;
        }
        return findActiveCategory(board, categoryId);
    }

    private String sanitizePostContents(String contents) {
        return Objects.toString(InputSanitizer.sanitizePostHtml(contents), "");
    }

}
