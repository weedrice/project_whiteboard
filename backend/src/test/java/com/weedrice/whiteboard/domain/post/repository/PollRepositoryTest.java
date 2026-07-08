package com.weedrice.whiteboard.domain.post.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class PollRepositoryTest {

    @Test
    @DisplayName("findByPostIdForUpdate declares a poll row write lock")
    void findByPostIdForUpdate_declaresPessimisticWriteLock() throws NoSuchMethodException {
        var method = PollRepository.class.getMethod("findByPostIdForUpdate", Long.class);

        Lock lock = method.getAnnotation(Lock.class);
        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths()).containsExactly("options", "post");
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("poll.post.postId = :postId");
    }
}
