package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PostServiceTransactionBoundaryTest {

    private final AnnotationTransactionAttributeSource transactionAttributeSource =
            new AnnotationTransactionAttributeSource();

    @Test
    @DisplayName("게시글 등록 응답 경로는 쓰기 트랜잭션을 사용한다")
    void createPostWithResponse_usesWriteTransaction() throws Exception {
        Method method = PostService.class.getMethod(
                "createPostWithResponse",
                Long.class,
                String.class,
                PostCreateRequest.class);

        TransactionAttribute attribute =
                transactionAttributeSource.getTransactionAttribute(method, PostService.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.isReadOnly()).isFalse();
    }
}
