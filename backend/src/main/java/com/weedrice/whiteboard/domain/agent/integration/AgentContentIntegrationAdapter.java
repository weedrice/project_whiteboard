package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.actor.ActorBatchReadPort;
import com.weedrice.whiteboard.domain.actor.AuthorSnapshot;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentProfileResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.port.AgentContentPort;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService;
import com.weedrice.whiteboard.domain.agent.service.AgentWritePolicy;
import com.weedrice.whiteboard.domain.agent.service.AgentWriteRequestMapper;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.BlockedUserIdsParameter;
import com.weedrice.whiteboard.domain.comment.service.CommentCreateContext;
import com.weedrice.whiteboard.domain.comment.service.CommentLikeCommand;
import com.weedrice.whiteboard.domain.comment.service.CommentReadModel;
import com.weedrice.whiteboard.domain.comment.service.CommentReadModelAssembler;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSupport;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.service.PostCommandService;
import com.weedrice.whiteboard.domain.post.service.PostCreateContext;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentContentIntegrationAdapter implements AgentContentPort {

    private static final String ACTION_CREATE_POST = "create_post";
    private static final String ACTION_CREATE_COMMENT = "create_comment";
    private static final String ACTION_CREATE_REPLY = "create_reply";

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final PostCommandService postCommandService;
    private final CommentService commentService;
    private final CommentLikeCommand commentLikeCommand;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentCommentAccessService agentCommentAccessService;
    private final AgentPolicyService agentPolicyService;
    private final AgentWritePolicy agentWritePolicy;
    private final AgentWriteTargetResolver agentWriteTargetResolver;
    private final AgentWriteRequestMapper agentWriteRequestMapper;
    private final AgentPostListItemAssembler agentPostListItemAssembler;
    private final CommentReadSupport commentReadSupport;
    private final CommentReadModelAssembler commentReadModelAssembler;
    private final ActorBatchReadPort actorBatchReadPort;
    private final BoardRepository boardRepository;
    private final PostAccessPolicy postAccessPolicy;
    private final UserBlockService userBlockService;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional
    public Long createPost(Agent agent, AgentPostCreateRequest request) {
        AgentPolicyService.AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        agentWritePolicy.validateCanPost(agent, policy, ACTION_CREATE_POST);
        if (request.getImageFileId() == null && request.getImageAlt() != null && !request.getImageAlt().isBlank()) {
            throw agentWritePolicy.writeException(AgentWriteErrorCode.VALIDATION_FAILED,
                    "imageAlt requires imageFileId.", ACTION_CREATE_POST, policy, null, null);
        }
        Board board = agentWriteTargetResolver.resolveBoardForPost(request.getBoardUrl(), ACTION_CREATE_POST, policy);
        agentWritePolicy.validateBoardReadable(agent, board, ACTION_CREATE_POST, policy);
        BoardCategory category = agentWriteTargetResolver.resolveCategory(
                board, request.getCategoryId(), ACTION_CREATE_POST, policy);
        agentWritePolicy.validateBoardWritable(agent, board, category, ACTION_CREATE_POST, policy);
        agentWritePolicy.validateEncoding(
                ACTION_CREATE_POST, policy, request.getTitle(), request.getContent(), request.getImageAlt());
        agentWritePolicy.validatePostTitle(request.getTitle(), ACTION_CREATE_POST, policy);
        agentWritePolicy.reservePostCreation(agent, ACTION_CREATE_POST, policy);
        return postService.createPostAsAgent(
                agent.getUserId(), agent.getAgentId(), agentWriteRequestMapper.toPostCreateRequest(request),
                PostCreateContext.agent(agent.getUserId(), agent.getAgentId(), board, category));
    }

    @Override
    @Transactional
    public DeletePostResult deletePost(Agent agent, Long postId) {
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!Objects.equals(post.getAgentId(), agent.getAgentId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        boolean alreadyDeleted = Boolean.TRUE.equals(post.getIsDeleted());
        java.time.LocalDateTime deletedAt = alreadyDeleted && post.getModifiedAt() != null
                ? post.getModifiedAt()
                : java.time.LocalDateTime.now(clock);
        if (!alreadyDeleted) {
            postCommandService.deleteAgentOwnedPost(
                    post, agent.getAgentId(), new ContentActorRef(agent.getUserId(), agent.getAgentId()));
        }
        return new DeletePostResult(alreadyDeleted, deletedAt);
    }

    @Override
    @Transactional
    public Long createComment(Agent agent, Long postId, AgentCommentCreateRequest request) {
        AgentPolicyService.AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        agentWritePolicy.validateCanComment(agent, policy, ACTION_CREATE_COMMENT);
        Post post = agentWriteTargetResolver.resolvePostForComment(agent, postId, ACTION_CREATE_COMMENT, policy);
        agentWritePolicy.validateBoardWritable(
                agent, post.getBoard(), post.getCategory(), ACTION_CREATE_COMMENT, policy);
        agentWritePolicy.validateEncoding(ACTION_CREATE_COMMENT, policy, request.getContent());
        agentWritePolicy.reserveCommentCreation(agent, ACTION_CREATE_COMMENT, policy);
        return commentService.createCommentAsAgent(agent.getUserId(), agent.getAgentId(), postId, null,
                request.getContent(), CommentCreateContext.agentRoot(agent.getAgentId(), postId));
    }

    @Override
    @Transactional
    public Long createReply(Agent agent, Long commentId, AgentCommentCreateRequest request) {
        commentService.lockAuthorForWrite(agent.getUserId());
        AgentPolicyService.AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        agentWritePolicy.validateCanComment(agent, policy, ACTION_CREATE_REPLY);
        Comment parent = agentWriteTargetResolver.resolveParentCommentForReply(commentId, ACTION_CREATE_REPLY, policy);
        Post post = postRepository.findByIdWithRelations(parent.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        agentWritePolicy.validateBoardWritable(agent, post.getBoard(), post.getCategory(), ACTION_CREATE_REPLY, policy);
        agentWritePolicy.validateEncoding(ACTION_CREATE_REPLY, policy, request.getContent());
        agentWritePolicy.reserveCommentCreation(agent, ACTION_CREATE_REPLY, policy);
        return commentService.createCommentAsAgent(agent.getUserId(), agent.getAgentId(), parent.getPostId(), commentId,
                request.getContent(), CommentCreateContext.agentReply(agent.getAgentId(), parent));
    }

    @Override
    @Transactional
    public LikeResult likePost(Agent agent, Long postId) {
        Post post = postService.getPostById(postId, agent.getUserId(), false);
        agentBoardAccessService.validateAgentBoardWritable(agent, post.getBoard());
        return new LikeResult(postService.likePost(agent.getUserId(), agent.getAgentId(), post), false);
    }

    @Override
    @Transactional
    public LikeResult likeComment(Agent agent, Long commentId) {
        Comment comment = commentRepository.findByIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        agentCommentAccessService.validateReadableActiveComment(agent, comment);
        CommentLikeCommand.CommentLikeResult result = commentLikeCommand.like(
                agent.getUserId(), comment, CommentLikeCommand.DuplicatePolicy.RETURN_ALREADY_LIKED);
        return new LikeResult(result.likeCount(), result.alreadyLiked());
    }

    @Override
    public AgentProfileContent getProfileContent(Agent viewer, Agent target, int fetchSize, int resultLimit) {
        Long agentId = target.getAgentId();
        long postCount = postRepository.countPublicProfilePostsByAgentId(agentId);
        long commentCount = commentRepository.countPublicProfileCommentsByAgentId(agentId);
        long likes = postRepository.sumPublicProfilePostLikesByAgentId(agentId)
                + commentRepository.sumPublicProfileCommentLikesByAgentId(agentId);
        List<AgentProfileResponse.RecentPost> posts = postRepository
                .findPublicProfilePostsByAgentId(agentId, PageRequest.of(0, fetchSize)).getContent().stream()
                .filter(post -> isReadable(viewer, post.getBoard()))
                .limit(resultLimit)
                .map(this::recentPost)
                .toList();
        List<AgentProfileResponse.RecentComment> comments = commentRepository
                .findPublicProfileCommentsByAgentId(agentId, PageRequest.of(0, fetchSize)).getContent().stream()
                .map(comment -> Map.entry(comment, resolvePost(comment)))
                .filter(entry -> isReadable(viewer, entry.getValue().getBoard()))
                .limit(resultLimit)
                .map(entry -> recentComment(entry.getKey(), entry.getValue()))
                .toList();
        List<AgentProfileResponse.PrimaryBoard> boards = postRepository
                .findPrimaryBoardsByAgentPosts(agentId, PageRequest.of(0, resultLimit)).stream()
                .map(board -> AgentProfileResponse.PrimaryBoard.builder()
                        .boardId(board.getBoardId()).name(board.getBoardName()).boardUrl(board.getBoardUrl()).build())
                .toList();
        return new AgentProfileContent(postCount, commentCount, likes, posts, comments, boards);
    }

    @Override
    public Page<AgentPostListItem> getFeed(Agent agent, Long boardId, Pageable pageable) {
        List<Board> boards = agentBoardAccessService.getAccessibleFeedBoards(agent, boardId);
        if (boards.isEmpty()) {
            return Page.empty(pageable);
        }
        List<Long> boardIds = boards.stream().map(Board::getBoardId).toList();
        Set<Long> secretBoards = agentBoardAccessService.resolveBoardAdminIds(agent, boards, boardIds);
        Page<Post> posts = postRepository.findAgentFeedByBoardIds(
                boardIds, blockedIds(agent), secretBoards, agent.getUserId(), pageable);
        return agentPostListItemAssembler.fromPosts(posts, agent.getAgentId());
    }

    @Override
    public Page<AgentPostListItem> getMyPosts(Agent agent, Pageable pageable) {
        return agentPostListItemAssembler.fromPosts(
                postRepository.findByAgentIdAndIsDeleted(agent.getAgentId(), false, pageable), agent.getAgentId());
    }

    @Override
    public Page<AgentPostListItem> getBoardPosts(Agent agent, Long boardId, Long categoryId, Pageable pageable) {
        Board board = boardRepository.findByBoardId(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        agentBoardAccessService.validateAgentBoardReadable(agent, board);
        Page<Post> posts = postService.getPosts(boardId, categoryId, null, null, agent.getUserId(),
                agentBoardAccessService.canViewSecretPosts(agent, board), pageable);
        return agentPostListItemAssembler.fromPosts(posts, agent.getAgentId());
    }

    @Override
    public Page<AgentCommentItem> getPostComments(Agent agent, Long postId, Pageable pageable) {
        Set<Long> blocked = validateCommentPostReadable(agent, postId);
        BlockedUserIdsParameter parameter = BlockedUserIdsParameter.from(blocked);
        Page<Comment> comments = commentRepository.findParentsWithChildrenOrNotDeleted(
                postId, parameter.empty(), parameter.ids(), pageable);
        return toCommentPage(comments, blocked);
    }

    @Override
    public Page<AgentCommentItem> getCommentReplies(Agent agent, Long commentId, Pageable pageable) {
        Comment parent = commentRepository.findByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        Set<Long> blocked = validateCommentPostReadable(agent, parent.getPostId());
        BlockedUserIdsParameter parameter = BlockedUserIdsParameter.from(blocked);
        if (commentReadSupport.isDeleted(parent)
                && !commentRepository.existsVisibleReplyByParentId(commentId, parameter.empty(), parameter.ids())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        Page<Comment> replies = commentRepository.findRepliesWithRelations(
                commentId, false, parameter.empty(), parameter.ids(), pageable);
        return toCommentPage(replies, blocked);
    }

    private Set<Long> validateCommentPostReadable(Agent agent, Long postId) {
        Set<Long> blocked = Set.copyOf(blockedIds(agent));
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        postAccessPolicy.validateReadable(post, resolveOwner(agent), blocked.contains(post.getUserId()));
        agentBoardAccessService.validateAgentBoardReadable(agent, post.getBoard());
        return blocked;
    }

    private Page<AgentCommentItem> toCommentPage(Page<Comment> comments, Set<Long> blocked) {
        if (comments.isEmpty()) {
            return new PageImpl<>(List.of(), comments.getPageable(), comments.getTotalElements());
        }
        Map<Long, Long> replyCounts = commentReadSupport.loadVisibleReplyCounts(comments.getContent(), blocked);
        Map<ContentActorRef, AuthorSnapshot> authors = actorBatchReadPort.resolveAuthors(
                comments.getContent().stream().map(this::actorRef).toList());
        List<AgentCommentItem> content = comments.getContent().stream()
                .map(comment -> toItem(commentReadModelAssembler.from(
                        comment, authors.get(actorRef(comment)), blocked, replyCounts)))
                .toList();
        return new PageImpl<>(content, comments.getPageable(), comments.getTotalElements());
    }

    @Override
    public boolean isOwnerEmailVerified(Agent agent) {
        return Boolean.TRUE.equals(resolveOwner(agent).getIsEmailVerified());
    }

    @Override
    public void validateProfileVisible(Agent viewer, Agent target) {
        if (!Objects.equals(viewer.getUserId(), target.getUserId())
                && userBlockService.isEitherDirectionBlocked(viewer.getUserId(), target.getUserId())) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
    }

    private User resolveOwner(Agent agent) {
        return userRepository.findById(agent.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private List<Long> blockedIds(Agent agent) {
        return userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(agent.getUserId());
    }

    private boolean isReadable(Agent agent, Board board) {
        try {
            agentBoardAccessService.validateAgentBoardReadable(agent, board);
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private AgentProfileResponse.RecentPost recentPost(Post post) {
        Board board = post.getBoard();
        return AgentProfileResponse.RecentPost.builder()
                .postId(post.getPostId()).title(post.getTitle())
                .contentPreview(preview(post.getContents()))
                .boardId(board.getBoardId()).boardName(board.getBoardName()).boardUrl(board.getBoardUrl())
                .likeCount(post.getLikeCount()).commentCount(post.getCommentCount())
                .createdAt(toOffsetDateTime(post.getCreatedAt())).build();
    }

    private AgentProfileResponse.RecentComment recentComment(Comment comment, Post post) {
        Board board = post.getBoard();
        return AgentProfileResponse.RecentComment.builder()
                .commentId(comment.getCommentId()).postId(post.getPostId()).postTitle(post.getTitle())
                .contentPreview(preview(comment.getContent()))
                .boardId(board.getBoardId()).boardName(board.getBoardName()).boardUrl(board.getBoardUrl())
                .likeCount(comment.getLikeCount())
                .createdAt(toOffsetDateTime(comment.getCreatedAt())).build();
    }

    private Post resolvePost(Comment comment) {
        return postRepository.findByIdWithRelations(comment.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private ContentActorRef actorRef(Comment comment) {
        return new ContentActorRef(comment.getUserId(), comment.getAgentId());
    }

    private AgentCommentItem toItem(CommentReadModel model) {
        Comment comment = model.comment();
        return AgentCommentItem.builder()
                .commentId(comment.getCommentId())
                .parentId(comment.getParent() == null ? null : comment.getParent().getCommentId())
                .postId(comment.getPostId())
                .content(model.status() == CommentReadModel.Status.ACTIVE ? comment.getContent() : null)
                .depth(comment.getDepth()).likeCount(comment.getLikeCount())
                .replyCount(model.replyCount()).hasReplies(model.hasReplies()).createdAt(comment.getCreatedAt())
                .status(switch (model.status()) {
                    case ACTIVE -> AgentCommentItem.STATUS_ACTIVE;
                    case DELETED -> AgentCommentItem.STATUS_DELETED;
                    case BLINDED -> AgentCommentItem.STATUS_BLINDED;
                    case BLOCKED_AUTHOR -> AgentCommentItem.STATUS_BLOCKED_AUTHOR;
                })
                .author(model.author() == null ? null : AgentCommentItem.Author.builder()
                        .userId(model.author().userId()).agentId(model.author().agentId())
                        .authorType(model.author().authorType()).displayName(model.author().displayName()).build())
                .build();
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String plain = InputSanitizer.stripHtml(content).replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ").trim();
        return plain.length() <= 120 ? plain : plain.substring(0, 120);
    }

    private java.time.OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(DateTimeUtils.KST_ZONE_ID).toOffsetDateTime();
    }
}
