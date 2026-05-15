package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommandService {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final PostVersionRepository postVersionRepository;
    private final TagAssignmentService tagAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final ContentRewardService contentRewardService;
    private final FileService fileService;
    private final AgentOwnershipService agentOwnershipService;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;

    @Transactional
    public Long createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return createPost(userId, null, board.getBoardId(), request, null).getPostId();
    }

    @Transactional
    public Long createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, String boardUrl,
            PostCreateRequest request) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return createPost(userId, agentId, board.getBoardId(), request, null).getPostId();
    }

    @Transactional
    public Long createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, PostCreateRequest request,
            PostCreateContext context) {
        return createPost(userId, agentId, null, request, context).getPostId();
    }

    @Transactional
    public Long createPost(@NonNull Long userId, @NonNull Long boardId, PostCreateRequest request) {
        return createPost(userId, null, boardId, request, null).getPostId();
    }

    @Transactional
    public Long createPost(@NonNull Long userId, Long agentId, @NonNull Long boardId, PostCreateRequest request) {
        return createPost(userId, agentId, boardId, request, null).getPostId();
    }

    private Post createPost(@NonNull Long userId, Long agentId, Long boardId, PostCreateRequest request,
            PostCreateContext context) {
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        Agent agent = resolveAgent(userId, agentId, context);
        Board board = resolveBoard(boardId, context);

        boolean boardWritablePrevalidated = isBoardWritablePrevalidated(context);
        if (!boardWritablePrevalidated) {
            postAuthorCommandPolicy.validateBoardWritable(board, user);
        }

        if (request.isNotice()) {
            if (!boardAccessPolicy.hasBoardAdminAccess(board, user)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        BoardCategory category = resolveCreatedCategory(board, request.getCategoryId(), context);
        if (!isCategoryWriteRolePrevalidated(context, request.getCategoryId(), category)) {
            postAuthorCommandPolicy.validateAppliedCategoryWriteRole(board, user, category);
        }

        PostTitleValidator.validate(request.getTitle());
        String sanitizedContents = sanitizePostContents(request.getContents());
        boolean isSecret = !boardAccessPolicy.isInquiryBoard(board) && request.isSecret();

        Post post = Post.builder()
                .board(board)
                .user(user)
                .agent(agent)
                .category(category)
                .title(request.getTitle())
                .contents(sanitizedContents)
                .isNotice(request.isNotice())
                .isNsfw(request.isNsfw())
                .isSpoiler(request.isSpoiler())
                .isSecret(isSecret)
                .build();

        Post savedPost = postRepository.save(post);
        tagAssignmentService.assignTags(savedPost, request.getTags());
        savePostVersion(savedPost, user, "CREATE", null, null);

        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            fileService.attachFilesToPost(request.getFileIds(), userId, savedPost.getPostId(), request.getDraftId());
        }

        contentRewardService.rewardCreate(userId, savedPost.getPostId(), ContentRewardPolicy.POST);
        eventPublisher.publishEvent(new PostPublishedEvent(savedPost.getPostId(), board.getBoardId()));
        return savedPost;
    }

    private boolean isBoardWritablePrevalidated(PostCreateContext context) {
        return context != null && context.boardWritablePrevalidated();
    }

    private boolean isCategoryWriteRolePrevalidated(PostCreateContext context, Long categoryId,
            BoardCategory category) {
        if (!isBoardWritablePrevalidated(context)) {
            return false;
        }
        if (categoryId == null) {
            return category == null;
        }
        return category != null
                && context.category() != null
                && Objects.equals(context.category().getCategoryId(), categoryId)
                && Objects.equals(context.category().getCategoryId(), category.getCategoryId());
    }

    private Agent resolveAgent(Long userId, Long agentId, PostCreateContext context) {
        if (context != null && context.agent() != null) {
            Agent contextAgent = context.agent();
            if (!Objects.equals(contextAgent.getAgentId(), agentId)
                    || contextAgent.getUser() == null
                    || !Objects.equals(contextAgent.getUser().getUserId(), userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return contextAgent;
        }
        if (agentId == null) {
            return null;
        }
        return agentOwnershipService.resolveOwnedActiveAgent(userId, agentId);
    }

    private Board resolveBoard(Long boardId, PostCreateContext context) {
        if (context != null && context.board() != null) {
            Board contextBoard = context.board();
            if (boardId != null && !Objects.equals(contextBoard.getBoardId(), boardId)) {
                throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
            }
            return contextBoard;
        }
        if (boardId == null) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
    }

    private BoardCategory resolveCreatedCategory(Board board, Long categoryId, PostCreateContext context) {
        if (context != null && context.category() != null) {
            BoardCategory contextCategory = context.category();
            if (!Objects.equals(contextCategory.getCategoryId(), categoryId)
                    || contextCategory.getBoard() == null
                    || !Objects.equals(contextCategory.getBoard().getBoardId(), board.getBoardId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return contextCategory;
        }
        if (categoryId == null) {
            return null;
        }
        return findActiveCategory(board, categoryId);
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

        if (request.getFileIds() != null) {
            fileService.syncPostFiles(request.getFileIds(), userId, post.getPostId(), request.getDraftId());
        }

        savePostVersion(post, modifier, "MODIFY", originalTitle, originalContents);

        return post.getPostId();
    }

    @Transactional
    public void deletePost(@NonNull Long userId, @NonNull Long postId) {
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User modifier = userWritableResolver.resolve(userId);

        postAuthorCommandPolicy.validateDeletable(post, modifier);

        post.deletePost();
        tagAssignmentService.clearTags(post);
        savePostVersion(post, modifier, "DELETE", post.getTitle(), post.getContents());
        fileService.markPostContentFilesDeletionPending(post.getPostId());

        contentRewardService.rollbackCreateReward(modifier, postId, ContentRewardPolicy.POST);
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

    private void savePostVersion(Post post, User modifier, String versionType, String originalTitle,
            String originalContents) {
        PostVersion postVersion = PostVersion.builder()
                .post(post)
                .modifier(modifier)
                .versionType(versionType)
                .originalTitle(originalTitle)
                .originalContents(originalContents)
                .build();
        postVersionRepository.save(postVersion);
    }

    private String sanitizePostContents(String contents) {
        return Objects.toString(InputSanitizer.sanitizePostHtml(contents), "");
    }

}
