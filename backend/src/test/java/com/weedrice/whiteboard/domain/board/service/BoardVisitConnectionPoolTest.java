package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.post.service.PostListReadService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:boardVisitSingleConnection;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=1000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, BoardService.class, BoardQueryService.class, BoardVisitService.class,
        BoardVisitWriter.class, BoardVisitConnectionPoolTest.Metrics.class})
@MockitoBean(types = {BoardProvisioningService.class, BoardCommandService.class, BoardProvisioningSideEffectService.class,
        BoardSubscriptionService.class, BoardCategoryService.class, BoardAccessPolicy.class, BoardAnonymousCacheService.class,
        AnonymousReadCacheInvalidator.class, BoardOrderingService.class, NotificationAccessInvalidationService.class,
        PostListReadService.class})
class BoardVisitConnectionPoolTest {
    @Autowired private BoardService boards;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SimpleMeterRegistry registry;
    @MockitoBean private BoardResponseAssembler assembler;

    @Test
    void readingBoardDetailsAndRecordingAVisitUseOneConnectionInSequence() {
        Long userId = transactions.execute(status -> {
            User reader = User.builder().loginId("visit-reader").displayName("Reader")
                    .email("reader@example.org").password("test-password").build();
            entityManager.persist(reader);
            entityManager.persist(Board.builder().creator(reader).boardName("Visit test").boardUrl("visit-test").build());
            return reader.getUserId();
        });
        BoardDetailResponse detail = mock(BoardDetailResponse.class);
        when(assembler.assembleDetail(any(), any())).thenReturn(detail);

        assertThat(boards.getBoardDetails("visit-test", userId)).isSameAs(detail);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM board_visits v JOIN boards b ON b.board_id = v.board_id
                 WHERE v.user_id = ? AND b.board_url = ?
                """, Long.class, userId, "visit-test")).isEqualTo(1L);
        assertThat(registry.counter("noviis.board.visit.failures").count()).isZero();
    }

    @TestConfiguration
    static class Metrics {
        @Bean SimpleMeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
}
