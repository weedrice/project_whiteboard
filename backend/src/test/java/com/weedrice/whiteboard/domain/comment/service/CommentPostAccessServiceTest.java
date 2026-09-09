package com.weedrice.whiteboard.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.weedrice.whiteboard.domain.comment.port.CommentPostPort;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommentPostAccessServiceTest {

    @Test
    void validateReadable_delegatesOnlyIdentifiersAndBlockContext() {
        CommentPostPort commentPostPort = mock(CommentPostPort.class);
        CommentPostAccessService service = new CommentPostAccessService(commentPostPort);
        CommentReadContext context = new CommentReadContext(1L, false, Set.of(3L));

        service.validateReadable(2L, context);

        verify(commentPostPort).validateReadable(2L, 1L, Set.of(3L));
    }
}
