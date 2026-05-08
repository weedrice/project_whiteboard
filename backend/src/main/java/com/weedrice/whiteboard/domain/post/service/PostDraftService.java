package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.DraftListResponse;
import com.weedrice.whiteboard.domain.post.dto.DraftResponse;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostDraftService {
    private static final int DEFAULT_DRAFT_PAGE_SIZE = 20;
    private static final Sort DEFAULT_DRAFT_SORT = Sort.by(
            Sort.Order.desc("modifiedAt"),
            Sort.Order.desc("draftId"));
    private static final Set<String> ALLOWED_DRAFT_SORTS = Set.of("modifiedAt", "createdAt", "draftId");

    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final PostRepository postRepository;
    private final DraftPostRepository draftPostRepository;
    private final FileService fileService;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;

    public DraftListResponse getDraftPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_DRAFT_PAGE_SIZE,
                DEFAULT_DRAFT_SORT,
                ALLOWED_DRAFT_SORTS);
        Page<DraftPost> draftPage = draftPostRepository.findPageByUserWithBoard(user, safePageable);
        return DraftListResponse.from(draftPage);
    }

    public DraftResponse getDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return DraftResponse.from(draftPost);
    }

    @Transactional
    public DraftPost saveDraftPost(@NonNull Long userId, PostDraftRequest request) {
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        Board board = boardRepository.findByBoardUrl(request.getBoardUrl())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        postAuthorCommandPolicy.validateBoardWritable(board, user);
        postAuthorCommandPolicy.validateAppliedCategoryWriteRole(board, user, null);

        BoardCategory category = null;
        if (request.getCategoryId() != null) {
            category = findActiveCategory(board, request.getCategoryId());
            postAuthorCommandPolicy.validateWriteRole(board, user, category.getMinWriteRole());
        }
        if (request.isNotice() && !boardAccessPolicy.hasBoardAdminAccess(board, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Post originalPost = null;
        if (request.getOriginalPostId() != null) {
            originalPost = postRepository.findById(request.getOriginalPostId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
            validateOriginalPostForDraft(originalPost, user, board, category);
        }

        DraftPost draftPost = resolveDraftPost(user, request, board, category, originalPost);
        DraftPost savedDraftPost = draftPostRepository.save(draftPost);
        fileService.syncDraftFiles(request.getFileIds(), userId, savedDraftPost.getDraftId());
        return savedDraftPost;
    }

    @Transactional
    public void deleteDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        User user = userWritableResolver.resolve(userId);
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        fileService.markDraftFilesDeletionPending(draftId);
        draftPostRepository.delete(draftPost);
    }

    private DraftPost resolveDraftPost(User user, PostDraftRequest request, Board board,
                                       BoardCategory category, Post originalPost) {
        if (request.getDraftId() == null) {
            return DraftPost.builder()
                    .user(user)
                    .board(board)
                    .category(category)
                    .title(request.getTitle())
                    .contents(request.getContents())
                    .tags(request.getTags())
                    .isNotice(request.isNotice())
                    .isNsfw(request.isNsfw())
                    .isSpoiler(request.isSpoiler())
                    .isSecret(request.isSecret())
                    .fileIds(request.getFileIds())
                    .originalPost(originalPost)
                    .build();
        }

        DraftPost draftPost = draftPostRepository.findByDraftIdAndUser(request.getDraftId(), user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (request.getUpdatedAt() != null
                && draftPost.getModifiedAt() != null
                && request.getUpdatedAt().isBefore(draftPost.getModifiedAt())) {
            throw new BusinessException(ErrorCode.DRAFT_OUTDATED);
        }
        draftPost.updateDraft(
                board,
                category,
                request.getTitle(),
                request.getContents(),
                request.getTags(),
                request.isNotice(),
                request.isNsfw(),
                request.isSpoiler(),
                request.isSecret(),
                request.getFileIds(),
                originalPost);
        return draftPost;
    }

    private BoardCategory findActiveCategory(Board board, Long categoryId) {
        if (board == null || categoryId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(categoryId, board.getBoardId(), true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void validateOriginalPostForDraft(Post originalPost, User user, Board board, BoardCategory category) {
        postAuthorCommandPolicy.validateAuthorCommand(originalPost, user);
        if (!Objects.equals(originalPost.getBoard().getBoardId(), board.getBoardId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        postAuthorCommandPolicy.validateWritableCommand(originalPost, user, originalPost.getCategory());
    }
}
