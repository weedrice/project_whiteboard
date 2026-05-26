package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentSearchCommandService {
    private final RecentSearchWriteService recentSearchWriteService;
    private final SearchUserLookupPolicy searchUserLookupPolicy;
    private final SearchUpsertRetryPolicy searchUpsertRetryPolicy;

    public void recordRecentSearch(Long userId, String keyword) {
        if (userId == null) {
            return;
        }

        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);
        if (canonicalKeyword == null) {
            return;
        }

        searchUserLookupPolicy.validateExists(userId);
        String normalizedKeyword = SearchKeywordNormalizer.normalize(canonicalKeyword);
        LocalDateTime searchedAt = DateTimeUtils.nowKST();

        searchUpsertRetryPolicy.updateOrCreateOrThrow(
                () -> recentSearchWriteService.updateRecentSearch(
                        userId, canonicalKeyword, normalizedKeyword, searchedAt),
                () -> recentSearchWriteService.createRecentSearch(
                        userId, canonicalKeyword, normalizedKeyword, searchedAt));
    }

}
