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
import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
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

    private static final String POINT_POST_CREATE_REWARD_CONFIG_KEY = "POINT_POST_CREATE_REWARD";
    private static final int DEFAULT_POST_CREATE_REWARD = 50;

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final UserRepository userRepository;
    private final PostVersionRepository postVersionRepository;
    private final TagAssignmentService tagAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final PointService pointService;
    private final PointHistoryRepository pointHistoryRepository;
    private final FileService fileService;
    private final GlobalConfigService globalConfigService;
    private final AgentOwnershipService agentOwnershipService;
    private final SanctionService sanctionService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;

    @Transactional
    public Post createPost(@NonNull Long userId, String boardUrl, PostCreateRequest request) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return createPost(userId, null, board.getBoardId(), request);
    }

    @Transactional
    public Post createPostAsAgent(@NonNull Long userId, @NonNull Long agentId, String boardUrl,
            PostCreateRequest request) {
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return createPost(userId, agentId, board.getBoardId(), request);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, @NonNull Long boardId, PostCreateRequest request) {
        return createPost(userId, null, boardId, request);
    }

    @Transactional
    public Post createPost(@NonNull Long userId, Long agentId, @NonNull Long boardId, PostCreateRequest request) {
        User user = getWritableUser(userId);
        sanctionService.validateNotMuted(user);
        Agent agent = agentOwnershipService.resolveOwnedActiveAgent(userId, agentId);
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        postAuthorCommandPolicy.validateBoardWritable(board, user);

        if (request.isNotice()) {
            if (!boardAccessPolicy.hasBoardAdminAccess(board, user)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        BoardCategory category = null;
        if (request.getCategoryId() != null) {
            category = findActiveCategory(board, request.getCategoryId());
        }
        postAuthorCommandPolicy.validateAppliedCategoryWriteRole(board, user, category);

        String sanitizedContents = InputSanitizer.sanitize(request.getContents());
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
            fileService.attachFilesToPost(request.getFileIds(), userId, savedPost.getPostId());
        }

        String postCreateRewardConfig = globalConfigService.getConfig(POINT_POST_CREATE_REWARD_CONFIG_KEY);
        int postCreateReward = GlobalConfigService.parseIntConfigOrDefault(
                postCreateRewardConfig,
                DEFAULT_POST_CREATE_REWARD,
                0);
        if (postCreateReward > 0) {
            pointService.addPoint(userId, postCreateReward, "\uAC8C\uC2DC\uAE00 \uC791\uC131", savedPost.getPostId(), "POST");
        }
        eventPublisher.publishEvent(new PostPublishedEvent(savedPost.getPostId(), board.getBoardId()));
        return savedPost;
    }

    @Transactional
    public Post updatePost(@NonNull Long userId, @NonNull Long postId, PostUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User modifier = getWritableUser(userId);
        sanctionService.validateNotMuted(modifier);

        postAuthorCommandPolicy.validateAuthorCommand(post, modifier);
        BoardCategory category = resolveUpdatedCategory(post, request.getCategoryId());
        postAuthorCommandPolicy.validateWritableCommand(post, modifier, category);
        tagAssignmentService.validateTags(request.getTags());

        String originalTitle = post.getTitle();
        String originalContents = post.getContents();
        String sanitizedContents = InputSanitizer.sanitize(request.getContents());

        boolean isSecret = !boardAccessPolicy.isInquiryBoard(post.getBoard()) && request.isSecret();
        post.updatePost(category, request.getTitle(), sanitizedContents, request.isNsfw(),
                request.isSpoiler(), isSecret);
        tagAssignmentService.assignTags(post, request.getTags());

        if (request.getFileIds() != null) {
            fileService.syncPostFiles(request.getFileIds(), userId, post.getPostId());
        }

        savePostVersion(post, modifier, "MODIFY", originalTitle, originalContents);

        return post;
    }

    @Transactional
    public void deletePost(@NonNull Long userId, @NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User modifier = getWritableUser(userId);

        postAuthorCommandPolicy.validateDeletable(post, modifier);

        post.deletePost();
        tagAssignmentService.clearTags(post);
        savePostVersion(post, modifier, "DELETE", post.getTitle(), post.getContents());
        fileService.markPostContentFilesDeletionPending(post.getPostId());

        int rewardedAmount = getPostCreateRewardAmount(modifier, postId);
        if (rewardedAmount > 0) {
            pointService.forceSubtractPoint(userId, rewardedAmount, "\uAC8C\uC2DC\uAE00 \uC0AD\uC81C", postId, "POST");
        }
    }

    private User getWritableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(user);
        return user;
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

    private int getPostCreateRewardAmount(User user, Long postId) {
        return pointHistoryRepository.findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
                        user,
                        "EARN",
                        "POST",
                        postId)
                .stream()
                .map(PointHistory::getAmount)
                .filter(Objects::nonNull)
                .filter(amount -> amount > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }
}
