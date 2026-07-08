package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.board.dto.BoardSummary;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostSummaryAssembler;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSummary;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserBlockService userBlockService;
    private final PostSummaryAssembler postSummaryAssembler;
    private final IntegratedSearchAssembler integratedSearchAssembler;
    private final SearchService searchService;

    public IntegratedSearchResponse integratedSearch(String keyword, String searchType, String boardUrl, String author,
            String from, String to, String period, Sort sort, Long currentUserId) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword);
        Pageable previewPageable = SearchRequestNormalizer.normalizePostSearchPageable(0, SEARCH_PREVIEW_LIMIT, sort);
        Pageable commentPreviewPageable = PageRequest.of(0, SEARCH_PREVIEW_LIMIT, COMMENT_PREVIEW_SORT);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIdsEitherDirection(currentUserId);
        }

        Page<PostSummary> posts = searchService.previewPosts(
                canonicalKeyword, searchType, boardUrl, author, from, to, period, SEARCH_PREVIEW_LIMIT, sort,
                currentUserId);

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
}
