package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SemanticSearchQueryTransactionServiceTest {

    private SemanticSearchQueryTransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new SemanticSearchQueryTransactionService(
                mock(SemanticSearchVectorRepository.class),
                mock(SemanticSearchKeywordFallbackRepository.class),
                mock(BoardRepository.class),
                new BoardAccessPolicy(mock(AdminRepository.class)),
                mock(UserRepository.class),
                mock(UserBlockService.class));
    }

    @Test
    void queryStepsDeclareReadOnlyTransactions() throws NoSuchMethodException {
        assertThat(transactionalAnnotation("loadQueryContext", String.class, Long.class).readOnly()).isTrue();
        assertThat(transactionalAnnotation("searchVector", SemanticSearchQuery.class).readOnly()).isTrue();
        assertThat(transactionalAnnotation("searchKeyword", SemanticSearchKeywordQuery.class).readOnly()).isTrue();
    }

    private Transactional transactionalAnnotation(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = transactionService.getClass().getMethod(methodName, parameterTypes);
        return method.getAnnotation(Transactional.class);
    }
}
