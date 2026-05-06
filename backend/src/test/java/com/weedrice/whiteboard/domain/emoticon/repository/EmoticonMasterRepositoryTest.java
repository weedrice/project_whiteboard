package com.weedrice.whiteboard.domain.emoticon.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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
}
