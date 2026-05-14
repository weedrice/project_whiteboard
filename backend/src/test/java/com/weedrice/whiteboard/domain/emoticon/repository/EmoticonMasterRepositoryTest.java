package com.weedrice.whiteboard.domain.emoticon.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("latest integrated search query orders by createdAt descending")
    void searchByKeywordAllOrderByCreatedAtDesc_declaresLatestOrdering() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod(
                "searchByKeywordAllOrderByCreatedAtDesc",
                String.class,
                Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY em.created_at DESC");
    }

    @Test
    @DisplayName("oldest integrated search query orders by createdAt ascending")
    void searchByKeywordAllOrderByCreatedAtAsc_declaresOldestOrdering() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod(
                "searchByKeywordAllOrderByCreatedAtAsc",
                String.class,
                Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY em.created_at ASC");
    }

    @Test
    @DisplayName("oldest name search query orders by createdAt ascending")
    void searchByNameOrderByCreatedAtAsc_declaresOldestOrdering() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod(
                "searchByNameOrderByCreatedAtAsc",
                String.class,
                Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY created_at ASC");
    }

    @Test
    @DisplayName("oldest creator search query orders by createdAt ascending")
    void searchByCreatorOrderByCreatedAtAsc_declaresOldestOrdering() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod(
                "searchByCreatorOrderByCreatedAtAsc",
                String.class,
                Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY em.created_at ASC");
    }

    @Test
    @DisplayName("oldest tag search query orders by createdAt ascending")
    void searchByTagOrderByCreatedAtAsc_declaresOldestOrdering() throws NoSuchMethodException {
        var method = EmoticonMasterRepository.class.getMethod(
                "searchByTagOrderByCreatedAtAsc",
                String.class,
                Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY created_at ASC");
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
}
