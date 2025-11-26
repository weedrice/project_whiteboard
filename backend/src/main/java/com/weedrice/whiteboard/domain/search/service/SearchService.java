package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import com.weedrice.whiteboard.domain.search.entity.SearchStatistic;
import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import com.weedrice.whiteboard.domain.search.repository.SearchStatisticRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final SearchStatisticRepository searchStatisticRepository;
    private final SearchPersonalizationRepository searchPersonalizationRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public void recordSearch(Long userId, String keyword) {
        // 1. 검색 통계 업데이트
        LocalDate today = DateTimeUtils.nowKST().toLocalDate();
        SearchStatistic statistic = searchStatisticRepository.findByKeywordAndSearchDate(keyword, today)
                .orElseGet(() -> SearchStatistic.builder().keyword(keyword).searchDate(today).build());
        statistic.incrementSearchCount();
        searchStatisticRepository.save(statistic);

        // 2. 개인화 검색 이력 저장 (로그인한 사용자만)
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            // 중복 검색어 방지 (최근 검색어 업데이트)
            searchPersonalizationRepository.deleteByUserAndKeyword(user, keyword);

            SearchPersonalization personalization = SearchPersonalization.builder()
                    .user(user)
                    .keyword(keyword)
                    .build();
            searchPersonalizationRepository.save(personalization);
        }
    }

    public IntegratedSearchResponse integratedSearch(String keyword, String type, Pageable pageable) {
        Page<Post> postPage = null;
        Page<Comment> commentPage = null;
        Page<User> userPage = null;

        if ("all".equalsIgnoreCase(type) || "post".equalsIgnoreCase(type)) {
            postPage = postRepository.searchPostsByKeyword(keyword, pageable);
        }
        if ("all".equalsIgnoreCase(type) || "comment".equalsIgnoreCase(type)) {
            commentPage = commentRepository.searchCommentsByKeyword(keyword, pageable);
        }
        if ("all".equalsIgnoreCase(type) || "user".equalsIgnoreCase(type)) {
            userPage = userRepository.findByDisplayNameContainingIgnoreCaseAndStatus(keyword, "ACTIVE", pageable);
        }

        return IntegratedSearchResponse.from(postPage, commentPage, userPage, keyword);
    }

    public Page<SearchPersonalization> getRecentSearches(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return searchPersonalizationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    @Transactional
    public void deleteRecentSearch(Long userId, Long logId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        SearchPersonalization personalization = searchPersonalizationRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!personalization.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        searchPersonalizationRepository.delete(personalization);
    }

    @Transactional
    public void deleteAllRecentSearches(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        searchPersonalizationRepository.deleteByUser(user);
    }

    public List<PopularKeywordDto> getPopularKeywords(String period, int limit) {
        LocalDate endDate = DateTimeUtils.nowKST().toLocalDate();
        LocalDate startDate;

        switch (period.toUpperCase()) {
            case "DAILY":
                startDate = endDate;
                break;
            case "WEEKLY":
                startDate = endDate.minusWeeks(1);
                break;
            case "MONTHLY":
                startDate = endDate.minusMonths(1);
                break;
            default:
                startDate = endDate.minusWeeks(1); // 기본값은 주간
        }

        List<Object[]> results = searchStatisticRepository.findPopularKeywords(startDate, endDate);

        return results.stream()
                .map(result -> new PopularKeywordDto((String) result[0], ((Number) result[1]).longValue()))
                .sorted((k1, k2) -> Long.compare(k2.getCount(), k1.getCount())) // 내림차순 정렬
                .limit(limit)
                .collect(Collectors.toList());
    }
}
