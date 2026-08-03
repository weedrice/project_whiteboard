package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.DraftListResponse;
import com.weedrice.whiteboard.domain.post.dto.DraftMatchResponse;
import com.weedrice.whiteboard.domain.post.dto.DraftResponse;
import com.weedrice.whiteboard.domain.post.dto.PostDraftRequest;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesRepository;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostDraftService {
    private static final int DEFAULT_DRAFT_PAGE_SIZE = 20;
    private static final int MAX_DRAFT_POLL_QUESTION_LENGTH = 200;
    private static final int MAX_DRAFT_POLL_OPTION_COUNT = 10;
    private static final int MAX_DRAFT_POLL_OPTION_LENGTH = 100;
    private static final Sort DEFAULT_DRAFT_SORT = Sort.by(
            Sort.Order.desc("modifiedAt"),
            Sort.Order.desc("draftId"));

    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final PostRepository postRepository;
    private final PostSeriesRepository postSeriesRepository;
    private final DraftPostRepository draftPostRepository;
    private final ScheduledPostRepository scheduledPostRepository;
    private final FileService fileService;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final PostDraftCleanupService postDraftCleanupService;

    public DraftListResponse getDraftPosts(@NonNull Long userId, @NonNull Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_DRAFT_PAGE_SIZE, DEFAULT_DRAFT_SORT);
        Page<DraftPost> draftPage = draftPostRepository.findPageByUserWithBoard(user, safePageable);
        return DraftListResponse.from(draftPage);
    }

    public DraftResponse getDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));
        return DraftResponse.from(draftPost);
    }

    @Transactional
    public DraftResponse saveDraftPost(@NonNull Long userId, PostDraftRequest request) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(request.getBoardUrl());
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        postAuthorCommandPolicy.validateBoardWritable(board, user);
        validateDraftPoll(request.getPoll());

        BoardCategory category = null;
        if (request.getCategoryId() != null) {
            category = findActiveCategory(board, request.getCategoryId());
        }
        postAuthorCommandPolicy.validateAppliedCategoryWriteRole(board, user, category);
        if (request.isNotice() && !boardAccessPolicy.hasBoardAdminAccess(board, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Post originalPost = null;
        if (request.getOriginalPostId() != null) {
            originalPost = postRepository.findById(request.getOriginalPostId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
            validateOriginalPostForDraft(originalPost, user, board, category);
        }

        PostSeries series = null;
        if (request.getSeriesId() != null) {
            series = postSeriesRepository.findBySeriesIdAndOwner_UserId(request.getSeriesId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        }

        DraftPost draftPost = resolveDraftPost(user, request, board, category, series, originalPost);
        DraftPost savedDraftPost = draftPostRepository.saveAndFlush(draftPost);
        fileService.syncDraftFiles(request.getFileIds(), userId, savedDraftPost.getDraftId());
        postDraftCleanupService.enforceUserDraftLimit(user);
        return DraftResponse.from(savedDraftPost);
    }

    @Transactional
    public void deleteDraftPost(@NonNull Long userId, @NonNull Long draftId) {
        User user = userWritableResolver.resolve(userId);
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUserForUpdate(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));
        if (scheduledPostRepository.existsByDraftIdAndStatusIn(draftId, ScheduledPost.PROTECTED_DRAFT_STATUSES)) {
            throw new BusinessException(ErrorCode.DRAFT_PROTECTED);
        }
        fileService.markDraftFilesDeletionPending(draftId);
        draftPostRepository.delete(draftPost);
    }

    private DraftPost resolveDraftPost(User user, PostDraftRequest request, Board board,
                                       BoardCategory category, PostSeries series, Post originalPost) {
        String sanitizedContents = sanitizeDraftContents(request.getContents());
        DraftPost draftPost = null;
        if (request.getDraftId() != null) {
            draftPost = draftPostRepository.findByDraftIdAndUserForUpdate(request.getDraftId(), user)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));
        } else if (request.getClientDraftKey() != null && !request.getClientDraftKey().isBlank()) {
            draftPost = draftPostRepository.findByUserAndClientDraftKeyForUpdate(user, request.getClientDraftKey())
                    .orElse(null);
        }

        if (draftPost == null) {
            return DraftPost.builder()
                    .user(user)
                    .board(board)
                    .category(category)
                    .clientDraftKey(request.getClientDraftKey())
                    .title(request.getTitle())
                    .contents(sanitizedContents)
                    .tags(request.getTags())
                    .isNotice(request.isNotice())
                    .isNsfw(request.isNsfw())
                    .isSpoiler(request.isSpoiler())
                    .isSecret(request.isSecret())
                    .fileIds(request.getFileIds())
                    .poll(request.getPoll())
                    .series(series)
                    .originalPost(originalPost)
                    .build();
        }

        if (scheduledPostRepository.existsByDraftIdAndStatusIn(
                draftPost.getDraftId(), ScheduledPost.PROTECTED_DRAFT_STATUSES)) {
            throw new BusinessException(ErrorCode.DRAFT_PROTECTED);
        }
        if (request.getDraftId() == null
                && !isMatchingIdempotentCreateRetry(
                        draftPost, request, board, category, series, originalPost, sanitizedContents)) {
            throw new BusinessException(ErrorCode.DRAFT_OUTDATED);
        }
        if (request.getDraftId() != null
                && !isMatchingDraftVersion(
                        request.getVersion(), draftPost.getVersion(), request.getUpdatedAt(), draftPost.getModifiedAt())) {
            throw new BusinessException(ErrorCode.DRAFT_OUTDATED);
        }
        if (request.getClientDraftKey() != null
                && draftPost.getClientDraftKey() != null
                && !request.getClientDraftKey().equals(draftPost.getClientDraftKey())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        draftPost.adoptClientDraftKey(request.getClientDraftKey());
        draftPost.updateDraft(
                board,
                category,
                request.getTitle(),
                sanitizedContents,
                request.getTags(),
                request.isNotice(),
                request.isNsfw(),
                request.isSpoiler(),
                request.isSecret(),
                request.getFileIds(),
                request.getPoll(),
                series,
                originalPost);
        return draftPost;
    }

    public DraftMatchResponse getMatchingDraft(@NonNull Long userId, String boardUrl, Long originalPostId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        List<DraftPost> matches = draftPostRepository.findMatchingByUserAndTarget(
                user, normalizedBoardUrl, originalPostId, PageRequest.of(0, 2));
        boolean multipleMatchesFound = originalPostId == null && matches.size() > 1;
        return DraftMatchResponse.builder()
                .draftId(matches.size() == 1 || originalPostId != null && !matches.isEmpty()
                        ? matches.getFirst().getDraftId()
                        : null)
                .multipleMatchesFound(multipleMatchesFound)
                .build();
    }

    private boolean isMatchingIdempotentCreateRetry(DraftPost draftPost, PostDraftRequest request,
            Board board, BoardCategory category, PostSeries series, Post originalPost, String sanitizedContents) {
        return Objects.equals(draftPost.getBoard().getBoardId(), board.getBoardId())
                && Objects.equals(entityId(draftPost.getCategory()), entityId(category))
                && Objects.equals(draftPost.getTitle(), request.getTitle())
                && Objects.equals(draftPost.getContents(), sanitizedContents)
                && Objects.equals(orEmpty(draftPost.getTags()), orEmpty(request.getTags()))
                && draftPost.isNotice() == request.isNotice()
                && draftPost.isNsfw() == request.isNsfw()
                && draftPost.isSpoiler() == request.isSpoiler()
                && draftPost.isSecret() == request.isSecret()
                && Objects.equals(orEmpty(draftPost.getFileIds()), orEmpty(request.getFileIds()))
                && Objects.equals(draftPost.getPoll(), request.getPoll())
                && Objects.equals(entityId(draftPost.getSeries()), entityId(series))
                && Objects.equals(postId(draftPost.getOriginalPost()), postId(originalPost));
    }

    private Long entityId(BoardCategory category) {
        return category == null ? null : category.getCategoryId();
    }

    private Long entityId(PostSeries series) {
        return series == null ? null : series.getSeriesId();
    }

    private Long postId(Post post) {
        return post == null ? null : post.getPostId();
    }

    private <T> List<T> orEmpty(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private boolean isMatchingDraftVersion(Long requestVersion, Long draftVersion,
            LocalDateTime requestUpdatedAt, LocalDateTime draftModifiedAt) {
        if (requestVersion != null) {
            return requestVersion.equals(draftVersion);
        }
        if (requestUpdatedAt == null || draftModifiedAt == null) {
            return false;
        }
        return requestUpdatedAt.withNano(toMicrosecondPrecision(requestUpdatedAt.getNano()))
                .equals(draftModifiedAt.withNano(toMicrosecondPrecision(draftModifiedAt.getNano())));
    }

    private int toMicrosecondPrecision(int nanos) {
        return (nanos / 1_000) * 1_000;
    }

    private String sanitizeDraftContents(String contents) {
        return Objects.toString(InputSanitizer.sanitizePostHtml(contents), "");
    }

    private void validateDraftPoll(PollRequest poll) {
        if (poll == null) {
            return;
        }
        if (poll.getQuestion() != null && poll.getQuestion().length() > MAX_DRAFT_POLL_QUESTION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (poll.getOptions() == null) {
            return;
        }
        if (poll.getOptions().size() > MAX_DRAFT_POLL_OPTION_COUNT
                || poll.getOptions().stream()
                        .filter(Objects::nonNull)
                        .anyMatch(option -> option.length() > MAX_DRAFT_POLL_OPTION_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
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
