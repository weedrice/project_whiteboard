package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateResponse;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
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
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
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

    @Transactional
    public Long createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        return createPostWithResponse(userId, boardUrl, request).getPostId();
    }

    @Transactional
    public PostCreateResponse createPostWithResponse(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        CreatedPost createdPost = createPost(userId, null, board.getBoardId(), request,
                new PostCreateContext(null, board, null, false));
        return PostCreateResponse.builder()
                .postId(createdPost.post().getPostId())
                .earnedPoints(createdPost.earnedPoints() > 0 ? createdPost.earnedPoints() : null)
                .build();
    }

    @Transactional
    public Long createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, String boardUrl,
            PostCreateRequest request) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return createPost(userId, agentId, board.getBoardId(), request, new PostCreateContext(null, board, null, false))
                .post().getPostId();
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
                userId,
                target.user(),
                target.board().getBoardId(),
                savedPost,
                request);
        postSeriesService.attachPostToSeries(target.user().getUserId(), savedPost, request.getSeriesId());
        return new CreatedPost(savedPost, earnedPoints);
    }

    private record CreatedPost(Post post, int earnedPoints) {
    }

    @Transactional
    public Long updatePost(@NonNull Long userId, @NonNull Long postId, PostUpdateRequest request) {
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User modifier = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(modifier);

        postAuthorCommandPolicy.validateAuthorCommand(post, modifier);
        BoardCategory category = resolveUpdatedCategory(post, request.getCategoryId());
        postAuthorCommandPolicy.validateWritableCommand(post, modifier, category);
        tagAssignmentService.validateTags(request.getTags());
        PostTitleValidator.validate(request.getTitle());

        String originalTitle = post.getTitle();
        String originalContents = post.getContents();
        String sanitizedContents = sanitizePostContents(request.getContents());

        boolean isSecret = !boardAccessPolicy.isInquiryBoard(post.getBoard()) && request.isSecret();
        post.updatePost(category, request.getTitle(), sanitizedContents, request.isNsfw(),
                request.isSpoiler(), isSecret);
        tagAssignmentService.assignTags(post, request.getTags());
        postSeriesService.attachPostToSeries(post.getUser().getUserId(), post, request.getSeriesId());

        if (request.getFileIds() != null) {
            fileService.syncPostFiles(request.getFileIds(), userId, post.getPostId(), request.getDraftId());
        }
        postDraftPublicationService.deletePublishedDraftIfOwned(request.getDraftId(), modifier);

        postVersionRecorder.record(post, modifier, "MODIFY", originalTitle, originalContents);
        semanticSearchEventPublisher.publish("POST", post.getPostId(), SemanticSearchIndexAction.UPSERT);
        if (!Objects.equals(originalTitle, post.getTitle())) {
            semanticSearchEventPublisher.publishPostCommentsReindex(post.getPostId());
        }

        return post.getPostId();
    }

    @Transactional
    public void deletePost(@NonNull Long userId, @NonNull Long postId) {
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User modifier = userWritableResolver.resolve(userId);

        postAuthorCommandPolicy.validateDeletable(post, modifier);

        deletePostWithSideEffects(post, modifier);
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
    }

    private BoardCategory findActiveCategory(Board board, Long categoryId) {
        if (board == null || categoryId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(categoryId, board.getBoardId(), true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private BoardCategory resolveUpdatedCategory(Post post, Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        BoardCategory currentCategory = post.getCategory();
        if (currentCategory != null && Objects.equals(currentCategory.getCategoryId(), categoryId)) {
            return currentCategory;
        }

        return findActiveCategory(post.getBoard(), categoryId);
    }

    private String sanitizePostContents(String contents) {
        return Objects.toString(InputSanitizer.sanitizePostHtml(contents), "");
    }

}
