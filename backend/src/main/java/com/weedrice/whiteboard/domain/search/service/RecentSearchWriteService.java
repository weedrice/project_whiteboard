package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecentSearchWriteService {

    private final SearchPersonalizationRepository searchPersonalizationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int updateRecentSearch(Long userId, String keyword, String normalizedKeyword, LocalDateTime searchedAt) {
        return searchPersonalizationRepository.updateRecentSearch(userId, keyword, normalizedKeyword, searchedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createRecentSearch(Long userId, String keyword, String normalizedKeyword, LocalDateTime searchedAt) {
        searchPersonalizationRepository.insertRecentSearch(userId, keyword, normalizedKeyword, searchedAt);
    }
}
