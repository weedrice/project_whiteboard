package com.weedrice.whiteboard.domain.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;

class PostSeriesRepositoryTest {

    @Test
    void appendLockQuery_usesPessimisticWriteLock() throws Exception {
        Method method = PostSeriesRepository.class.getMethod(
                "findBySeriesIdAndOwnerUserIdForUpdate", Long.class, Long.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void seriesNavigationQuery_fetchesPostAuthorForVisibilityChecks() throws Exception {
        Method method = PostSeriesItemRepository.class.getMethod(
                "findBySeries_SeriesIdOrderBySortOrderAscItemIdAsc", Long.class);

        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths()).contains("post", "post.board", "post.user");
    }
}
