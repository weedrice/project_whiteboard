package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.board.dto.BoardSummary;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostSummaryAssembler;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.dto.UserSummary;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchPreviewReadService {
    private static final int SEARCH_PREVIEW_LIMIT = 5;
    private static final Sort COMMENT_PREVIEW_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("commentId"));

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final UserBlockService userBlockService;
    private final PostSummaryAssembler postSummaryAssembler;
    private final IntegratedSearchAssembler integratedSearchAssembler;
    private final SearchUserLookupPolicy searchUserLookupPolicy;

    public IntegratedSearchResponse integratedSearch(String keyword, Long currentUserId) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword);
        Pageable previewPageable = PageRequest.of(0, SEARCH_PREVIEW_LIMIT);
        Pageable commentPreviewPageable = PageRequest.of(0, SEARCH_PREVIEW_LIMIT, COMMENT_PREVIEW_SORT);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIdsEitherDirection(currentUserId);
        }

        Page<Post> postPage = postRepository.searchPostsByKeyword(canonicalKeyword,
                blockedUserIds, currentUserId, previewPageable);
        Page<PostSummary> posts = postSummaryAssembler.assembleSearchPage(postPage);

        Page<CommentResponse> comments = commentRepository
                .searchCommentsByKeyword(canonicalKeyword, blockedUserIds, currentUserId, commentPreviewPageable)
                .map(CommentResponse::from);

        Page<UserSummary> users = userRepository.searchUsersVisibleTo(canonicalKeyword, blockedUserIds, previewPageable)
                .map(UserSummary::from);

        List<BoardSummary> boards = boardRepository
                .findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                        canonicalKeyword,
                        previewPageable)
                .stream()
                .map(BoardSummary::from)
                .collect(Collectors.toList());

        return integratedSearchAssembler.assemble(posts, comments, users, boards, canonicalKeyword);
    }

    public IntegratedSearchResponse integratedSearch(String keyword, String searchType, String boardUrl, String author,
            String from, String to, String period, Sort sort, Long currentUserId) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword);
        Pageable previewPageable = SearchRequestNormalizer.normalizePostSearchPageable(0, SEARCH_PREVIEW_LIMIT, sort);
        Pageable commentPreviewPageable = PageRequest.of(0, SEARCH_PREVIEW_LIMIT, COMMENT_PREVIEW_SORT);

        BoardSearchContext boardContext = resolveBoardSearchContext(boardUrl, currentUserId);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = boardContext.viewer() == null
                    ? userBlockService.getBlockedUserIdsEitherDirection(currentUserId)
                    : userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(currentUserId);
        }

        DateRange dateRange = resolveDateRange(period, from, to);
        Page<Post> postPage = postRepository.searchPosts(
                canonicalKeyword,
                searchType,
                boardContext.boardUrl(),
                SearchRequestNormalizer.canonicalizeOptionalAuthor(author),
                dateRange.from(),
                dateRange.to(),
                blockedUserIds,
                boardContext.includeSecret(),
                currentUserId,
                previewPageable);
        Page<PostSummary> posts = postSummaryAssembler.assembleSearchPage(postPage);

        Page<CommentResponse> comments = commentRepository
                .searchCommentsByKeyword(
                        canonicalKeyword,
                        boardContext.boardUrl(),
                        blockedUserIds,
                        boardContext.includeSecret(),
                        currentUserId,
                        commentPreviewPageable)
                .map(CommentResponse::from);

        Page<UserSummary> users = userRepository.searchUsersVisibleTo(canonicalKeyword, blockedUserIds, previewPageable)
                .map(UserSummary::from);

        List<BoardSummary> boards = boardRepository
                .findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                        canonicalKeyword,
                        previewPageable)
                .stream()
                .map(BoardSummary::from)
                .collect(Collectors.toList());

        return integratedSearchAssembler.assemble(posts, comments, users, boards, canonicalKeyword);
    }

    private BoardSearchContext resolveBoardSearchContext(String boardUrl, Long currentUserId) {
        String canonicalBoardUrl = SearchRequestNormalizer.canonicalizeOptionalBoardUrl(boardUrl);
        if (canonicalBoardUrl == null) {
            return BoardSearchContext.global();
        }
        Board board = boardRepository.findByBoardUrl(canonicalBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User viewer = searchUserLookupPolicy.resolveOptional(currentUserId);
        if (!boardAccessPolicy.canReadBoard(board, viewer)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
        return new BoardSearchContext(
                canonicalBoardUrl,
                boardAccessPolicy.canViewSecretPosts(board, viewer),
                viewer);
    }

    private DateRange resolveDateRange(String period, String from, String to) {
        String normalizedPeriod = SearchRequestNormalizer.normalizeSearchPeriod(period);
        if (normalizedPeriod == null) {
            return new DateRange(null, null);
        }
        LocalDate today = com.weedrice.whiteboard.global.common.util.DateTimeUtils.nowKST().toLocalDate();
        return switch (normalizedPeriod) {
            case "TODAY" -> DateRange.between(today, today);
            case "WEEK" -> DateRange.between(today.minusWeeks(1), today);
            case "MONTH" -> DateRange.between(today.minusMonths(1), today);
            case "CUSTOM" -> DateRange.between(
                    SearchRequestNormalizer.parseOptionalDate(from),
                    SearchRequestNormalizer.parseOptionalDate(to));
            default -> new DateRange(null, null);
        };
    }

    private record DateRange(LocalDateTime from, LocalDateTime to) {
        static DateRange between(LocalDate from, LocalDate to) {
            LocalDateTime start = from == null ? null : from.atStartOfDay();
            LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
            return new DateRange(start, end);
        }
    }

    private record BoardSearchContext(
            String boardUrl,
            boolean includeSecret,
            User viewer) {
        static BoardSearchContext global() {
            return new BoardSearchContext(null, false, null);
        }
    }
}
