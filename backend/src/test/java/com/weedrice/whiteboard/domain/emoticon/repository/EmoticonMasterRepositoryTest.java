package com.weedrice.whiteboard.domain.emoticon.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
