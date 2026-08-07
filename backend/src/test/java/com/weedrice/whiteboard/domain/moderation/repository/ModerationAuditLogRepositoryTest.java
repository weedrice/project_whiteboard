package com.weedrice.whiteboard.domain.moderation.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.moderation.dto.ModerationAuditLogSearchCondition;
import com.weedrice.whiteboard.domain.moderation.entity.ModerationAuditLog;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class ModerationAuditLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ModerationAuditLogRepository repository;

    @BeforeEach
    void setUp() {
        User owner = persistUser("owner", "스페이스 소유자");
        User firstActor = persistUser("first-admin", "첫 번째 운영자");
        User secondActor = persistUser("second-admin", "두 번째 운영자");
        Board firstBoard = persistBoard("개발 커뮤니티", "development", owner);
        Board secondBoard = persistBoard("일상 이야기", "daily", owner);

        persistAudit(firstActor, firstBoard, 1L);
        persistAudit(secondActor, secondBoard, 2L);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("스페이스 이름과 사용자 이름은 대소문자 구분 없이 부분 검색한다")
    void search_filtersByBoardAndActorNames() {
        var byBoardName = repository.search(
                ModerationAuditLogSearchCondition.builder().boardName("커뮤니티").build(),
                PageRequest.of(0, 20));
        var byActorName = repository.search(
                ModerationAuditLogSearchCondition.builder().actorName("두 번째").build(),
                PageRequest.of(0, 20));

        assertThat(byBoardName.getContent())
                .extracting(audit -> audit.getBoard().getBoardName())
                .containsExactly("개발 커뮤니티");
        assertThat(byActorName.getContent())
                .extracting(audit -> audit.getActorUser().getDisplayName())
                .containsExactly("두 번째 운영자");
    }

    private User persistUser(String loginId, String displayName) {
        return entityManager.persist(User.builder()
                .loginId(loginId)
                .email(loginId + "@test.com")
                .password("password")
                .displayName(displayName)
                .build());
    }

    private Board persistBoard(String boardName, String boardUrl, User owner) {
        return entityManager.persist(Board.builder()
                .boardName(boardName)
                .boardUrl(boardUrl)
                .creator(owner)
                .build());
    }

    private void persistAudit(User actor, Board board, long targetId) {
        entityManager.persist(ModerationAuditLog.builder()
                .actorType(ModerationAuditLog.ACTOR_TYPE_USER)
                .actorUser(actor)
                .action("POST_BLIND")
                .targetType("POST")
                .targetId(targetId)
                .board(board)
                .createdAt(LocalDateTime.of(2026, 8, 6, 12, 0))
                .build());
    }
}
