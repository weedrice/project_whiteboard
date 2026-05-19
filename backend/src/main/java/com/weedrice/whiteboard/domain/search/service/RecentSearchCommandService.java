package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentSearchCommandService {
    private final RecentSearchWriteService recentSearchWriteService;
    private final SearchUserLookupPolicy searchUserLookupPolicy;

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

        int updated = recentSearchWriteService.updateRecentSearch(userId, canonicalKeyword, normalizedKeyword, searchedAt);
        if (updated > 0) {
            return;
        }

        try {
            recentSearchWriteService.createRecentSearch(userId, canonicalKeyword, normalizedKeyword, searchedAt);
        } catch (DataIntegrityViolationException e) {
            int recovered = recentSearchWriteService.updateRecentSearch(
                    userId,
                    canonicalKeyword,
                    normalizedKeyword,
                    searchedAt);
            if (recovered == 0) {
                throw e;
            }
        }
    }

}
