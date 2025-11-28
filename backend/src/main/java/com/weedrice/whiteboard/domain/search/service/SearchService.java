package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
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
import org.springframework.data.domain.PageRequest;
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
        LocalDate today = DateTimeUtils.nowKST().toLocalDate();
        SearchStatistic statistic = searchStatisticRepository.findByKeywordAndSearchDate(keyword, today)
                .orElseGet(() -> SearchStatistic.builder().keyword(keyword).searchDate(today).build());
        statistic.incrementSearchCount();
        searchStatisticRepository.save(statistic);

        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            searchPersonalizationRepository.deleteByUserAndKeyword(user, keyword);
            SearchPersonalization personalization = SearchPersonalization.builder()
                    .user(user)
                    .keyword(keyword)
                    .build();
            searchPersonalizationRepository.save(personalization);
        }
    }

    public IntegratedSearchResponse integratedSearch(String keyword) {
        Pageable previewPageable = PageRequest.of(0, 5); // 미리보기는 5개까지만
        Page<PostSummary> posts = postRepository.searchPostsByKeyword(keyword, previewPageable).map(PostSummary::from);
        // TODO: 댓글, 사용자 검색 결과 추가
        return IntegratedSearchResponse.from(posts, null, null, keyword);
    }

    public Page<PostSummary> searchPosts(String keyword, String searchType, String boardUrl, Pageable pageable) {
        return postRepository.searchPosts(keyword, searchType, boardUrl, pageable).map(PostSummary::from);
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
                startDate = endDate.minusWeeks(1);
        }

        List<Object[]> results = searchStatisticRepository.findPopularKeywords(startDate, endDate);

        return results.stream()
                .map(result -> new PopularKeywordDto((String) result[0], ((Number) result[1]).longValue()))
                .sorted((k1, k2) -> Long.compare(k2.getCount(), k1.getCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
