package com.weedrice.whiteboard.domain.emoticon.repository;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmoticonMasterRepositoryTest {

    @Test
    @DisplayName("findByIdForUpdate declares pessimistic write lock")
    void findByIdForUpdate_declaresPessimisticWriteLock() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod("findByIdForUpdate", Long.class);

        Lock lock = method.getAnnotation(Lock.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(query.value())
                .doesNotContain("JOIN FETCH")
                .contains("e.emoticonId = :emoticonId");
    }

    @Test
    @DisplayName("findFileAccessTargets resolves master by id or file URL references")
    void findFileAccessTargets_declaresMasterAccessQuery() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod("findFileAccessTargets", Long.class, List.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("LEFT JOIN FETCH e.creator")
                .contains("e.emoticonId = :emoticonId")
                .contains("e.thumbnailUrl IN :fileUrls")
                .contains("i.imageUrl IN :fileUrls");
    }

    @Test
    @DisplayName("canUseAnyEmoticon checks entitlement for any requested master")
    void canUseAnyEmoticon_declaresBulkEntitlementQuery() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod("canUseAnyEmoticon", Long.class, List.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("COUNT(e) > 0")
                .contains("e.emoticonId IN :emoticonIds")
                .contains("ep.purchaseId IS NOT NULL")
                .contains("e.creator.userId = :userId");
    }

    @Test
    @DisplayName("latest list query orders by createdAt descending")
    void findAllActive_declaresLatestOrdering() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod("findAllActive", Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY e.createdAt DESC");
    }

    @Test
    @DisplayName("oldest list query orders by createdAt ascending")
    void findAllActiveOrderByCreatedAtAsc_declaresOldestOrdering() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod("findAllActiveOrderByCreatedAtAsc", Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY e.createdAt ASC");
    }

    @Test
    @DisplayName("custom search keeps tag ANY condition and popular ordering")
    void searchActive_tagPopular_buildsExpectedNativeQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        jakarta.persistence.Query countQuery = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query contentQuery = mock(jakarta.persistence.Query.class);
        EmoticonMasterSearchRepositoryImpl repository = new EmoticonMasterSearchRepositoryImpl();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);

        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(countQuery);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString(), eq(EmoticonMaster.class)))
                .thenReturn(contentQuery);
        when(countQuery.setParameter("keyword", "웃음")).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(contentQuery.setParameter("keyword", "웃음")).thenReturn(contentQuery);
        when(contentQuery.setFirstResult(anyInt())).thenReturn(contentQuery);
        when(contentQuery.setMaxResults(anyInt())).thenReturn(contentQuery);
        when(contentQuery.getResultList()).thenReturn(List.of());

        repository.searchActive(
                new EmoticonSearchCondition(
                        "웃음",
                        EmoticonSearchCondition.SearchType.TAG,
                        EmoticonSearchCondition.SortType.POPULAR),
                PageRequest.of(0, 20));

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentSql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(countSql.capture());
        verify(entityManager).createNativeQuery(contentSql.capture(), eq(EmoticonMaster.class));

        assertThat(countSql.getValue()).contains(":keyword = ANY(em.tags)");
        assertThat(contentSql.getValue())
                .contains(":keyword = ANY(em.tags)")
                .contains("ORDER BY em.purchase_count DESC, em.created_at DESC");
    }

    @Test
    @DisplayName("custom integrated search uses left join and distinct")
    void searchActive_allLatest_buildsExpectedNativeQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        jakarta.persistence.Query countQuery = mock(jakarta.persistence.Query.class);
        EmoticonMasterSearchRepositoryImpl repository = new EmoticonMasterSearchRepositoryImpl();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);

        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(countQuery);
        when(countQuery.setParameter("keyword", "키워드")).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        repository.searchActive(
                new EmoticonSearchCondition(
                        "키워드",
                        EmoticonSearchCondition.SearchType.ALL,
                        EmoticonSearchCondition.SortType.LATEST),
                PageRequest.of(0, 20));

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(countSql.capture());

        assertThat(countSql.getValue())
                .contains("COUNT(DISTINCT em.emoticon_id)")
                .contains("LEFT JOIN users u ON em.creator_id = u.user_id")
                .contains("LOWER(em.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
                .contains("LOWER(u.display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
                .contains(":keyword = ANY(em.tags)");
    }

    @Test
    @DisplayName("custom search keeps unpaged repository semantics")
    void searchActive_unpaged_doesNotApplyLimit() {
        EntityManager entityManager = mock(EntityManager.class);
        jakarta.persistence.Query countQuery = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query contentQuery = mock(jakarta.persistence.Query.class);
        EmoticonMasterSearchRepositoryImpl repository = new EmoticonMasterSearchRepositoryImpl();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);

        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(countQuery);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString(), eq(EmoticonMaster.class)))
                .thenReturn(contentQuery);
        when(countQuery.setParameter("keyword", "테스트")).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(contentQuery.setParameter("keyword", "테스트")).thenReturn(contentQuery);
        when(contentQuery.getResultList()).thenReturn(List.of());

        repository.searchActive(
                new EmoticonSearchCondition(
                        "테스트",
                        EmoticonSearchCondition.SearchType.NAME,
                        EmoticonSearchCondition.SortType.LATEST),
                Pageable.unpaged());

        verify(contentQuery, org.mockito.Mockito.never()).setFirstResult(anyInt());
        verify(contentQuery, org.mockito.Mockito.never()).setMaxResults(anyInt());
    }

    @Test
    @DisplayName("latest tag search declares explicit count query")
    void findByTag_declaresCountQuery() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod("findByTag", String.class, Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains(":tag = ANY(tags)");
        assertThat(query.countQuery())
                .contains("SELECT COUNT(*) FROM emoticon_masters")
                .contains(":tag = ANY(tags)");
    }

    @Test
    @DisplayName("purchased emoticon list includes only purchase rows")
    void findPurchasedEmoticons_declaresPurchaseOnlyCondition() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod("findPurchasedEmoticons", Long.class, Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("JOIN emoticon_purchases")
                .doesNotContain("LEFT JOIN emoticon_purchases")
                .contains("ep.purchase_id IS NOT NULL")
                .doesNotContain("em.creator_id = :userId");
        assertThat(query.countQuery())
                .contains("JOIN emoticon_purchases")
                .doesNotContain("LEFT JOIN emoticon_purchases")
                .contains("ep.purchase_id IS NOT NULL")
                .doesNotContain("em.creator_id = :userId");
    }
}
