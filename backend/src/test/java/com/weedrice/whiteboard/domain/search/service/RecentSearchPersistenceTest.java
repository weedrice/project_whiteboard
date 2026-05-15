package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({QuerydslConfig.class, RecentSearchCommandService.class, RecentSearchWriteService.class,
        SearchUserLookupPolicy.class})
class RecentSearchPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SearchPersonalizationRepository searchPersonalizationRepository;

    @Autowired
    private RecentSearchCommandService recentSearchCommandService;

    @Test
    @DisplayName("같은 사용자의 정규화 키워드는 한 행으로 유지되고 searchedAt만 갱신된다")
    void recordRecentSearch_mergesNormalizedKeywordIntoSingleRow() throws Exception {
        User user = persistUser("search-user", "search-user@test.com");
        Long userId = user.getUserId();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        recentSearchCommandService.recordRecentSearch(userId, "Test");

        SearchPersonalization firstSaved = searchPersonalizationRepository.findAll().get(0);
        LocalDateTime firstSearchedAt = firstSaved.getSearchedAt();
        LocalDateTime firstModifiedAt = firstSaved.getModifiedAt();

        Thread.sleep(5L);

        recentSearchCommandService.recordRecentSearch(userId, " test ");

        assertThat(searchPersonalizationRepository.findAll()).hasSize(1);
        SearchPersonalization merged = searchPersonalizationRepository.findAll().get(0);
        assertThat(merged.getKeyword()).isEqualTo("test");
        assertThat(merged.getNormalizedKeyword()).isEqualTo("test");
        assertThat(merged.getSearchedAt()).isAfter(firstSearchedAt);
        assertThat(merged.getModifiedAt()).isAfter(firstModifiedAt);
    }

    @Test
    @DisplayName("최근 검색 조회는 searchedAt 동률일 때 logId 내림차순으로 정렬된다")
    void findByUserOrderBySearchedAtDesc_ordersBySearchedAtAndLogIdDesc() {
        User user = persistUser("order-user", "order-user@test.com");
        LocalDateTime searchedAt = LocalDateTime.of(2026, 4, 22, 12, 0);

        SearchPersonalization first = searchPersonalizationRepository.saveAndFlush(SearchPersonalization.builder()
                .user(user)
                .keyword("alpha")
                .normalizedKeyword("alpha")
                .searchedAt(searchedAt)
                .build());
        SearchPersonalization second = searchPersonalizationRepository.saveAndFlush(SearchPersonalization.builder()
                .user(user)
                .keyword("beta")
                .normalizedKeyword("beta")
                .searchedAt(searchedAt)
                .build());

        entityManager.clear();

        var page = searchPersonalizationRepository.findByUserOrderBySearchedAtDesc(user, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(SearchPersonalization::getLogId)
                .containsExactly(second.getLogId(), first.getLogId());
    }

    private User persistUser(String loginId, String email) {
        User user = User.builder()
                .loginId(loginId)
                .email(email)
                .password("password")
                .displayName(loginId)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        ReflectionTestUtils.setField(user, "userId", user.getUserId());
        return user;
    }
}
