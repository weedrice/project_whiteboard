package com.weedrice.whiteboard.domain.search.service;

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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final SearchStatisticRepository searchStatisticRepository;
    private final SearchPersonalizationRepository searchPersonalizationRepository;
    private final UserRepository userRepository;

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
            searchPersonalizationRepository.findByUserAndKeyword(user, keyword)
                    .ifPresent(searchPersonalizationRepository::delete);

            SearchPersonalization personalization = SearchPersonalization.builder()
                    .user(user)
                    .keyword(keyword)
                    .build();
            searchPersonalizationRepository.save(personalization);
        }
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
}
